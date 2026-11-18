package com.adesh.devjarvis.terminal

import com.adesh.devjarvis.tools.JarvisTool
import com.adesh.devjarvis.tools.SimpleTool
import com.adesh.devjarvis.tools.ToolArgs
import com.adesh.devjarvis.tools.ToolContext
import com.adesh.devjarvis.tools.ToolResult
import com.adesh.devjarvis.tools.params
import javax.inject.Inject
import javax.inject.Singleton

/** run_code: JARVIS writes code and runs it in its own terminal (Python 3 or the shell), and reads the output. */
@Singleton
class TerminalTools @Inject constructor(private val runner: CodeRunner) {
    private val risky = Regex("""(?i)(\brm\s+-[a-z]*r|\bdd\b|\bmkfs|chmod\s+-R|>\s*/dev/)""")

    private val runCode = object : SimpleTool(
        "run_code",
        "Write and run code in JARVIS's own built-in terminal (no Termux needed): language 'python' (Python 3 with the " +
            "standard library) or 'shell' (ls, cat, mkdir, echo, grep...). Give a filename to save the program first " +
            "(e.g. calculator.py) so Sir can run it again on the Terminal screen. 'input' = lines to feed input(). " +
            "Returns the real output — read it, fix errors yourself and run again; tell Sir the result in one line. " +
            "For Termux use run_in_termux only if Sir says Termux.",
        params {
            string("language", "python or shell", required = true, enum = listOf("python", "shell"))
            string("code", "The program or command", required = true)
            string("filename", "Save as this file in the home folder first, e.g. calculator.py")
            string("input", "Lines typed into the program, separated by \\n")
        },
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            val lang = args.requireString("language").lowercase()
            val code = args.requireString("code")
            val name = args.string("filename")?.takeIf { it.isNotBlank() }
            val input = args.string("input")?.split("\n").orEmpty()
            if (lang == "shell" && risky.containsMatchIn(code) && !ctx.confirm("Sir, ye command files mita sakti hai: ${code.take(80)} — chalaun?")) {
                return ToolResult.ok("cancelled — Sir said no")
            }
            runner.requestOpen() // Sir watches it happen on the Terminal screen
            val out = runCatching {
                if (name != null) {
                    runner.saveFile(name, code)
                    if (lang == "python") runner.run("python $name", input) else runner.run("sh $name", input)
                } else if (lang == "python") {
                    val tmp = "._jarvis_run.py"
                    runner.saveFile(tmp, code)
                    runner.run("python $tmp", input)
                } else {
                    runner.run(code, input)
                }
            }.getOrElse { return ToolResult.fail("could not run: ${it.message?.take(120)}") }
            val failed = Regex("""Traceback \(most recent call last\)|\(exit [1-9]|stopped after""").containsMatchIn(out)
            val text = "OUTPUT:\n${out.take(3_000)}" + (if (name != null) "\n(saved as ~/$name — Sir can run it on the Terminal screen: python $name)" else "")
            return if (failed) ToolResult.fail("$text\nIt failed — fix the code and run again.") else ToolResult.ok(text)
        }
    }

    private val openTerminal = object : SimpleTool(
        "open_terminal",
        "Open JARVIS's built-in terminal screen (\"terminal kholo\", \"apna terminal dikhao\").",
    ) {
        override suspend fun execute(args: ToolArgs, ctx: ToolContext): ToolResult {
            runner.requestOpen()
            return ToolResult.ok("terminal is open on the screen")
        }
    }

    val tools: List<JarvisTool> = listOf(runCode, openTerminal)
}
