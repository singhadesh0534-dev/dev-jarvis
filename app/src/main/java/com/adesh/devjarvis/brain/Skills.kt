package com.adesh.devjarvis.brain

/**
 * JARVIS ke "skills": kisi khaas tarah ke kaam ka pakka tareeka. Brain `use_skill` tool se sahi skill ka
 * playbook maangta hai aur usi ke hisaab se kaam karta hai. Saare playbooks is app ke liye likhe gaye hain.
 * Pure, unit-tested.
 */
object Skills {
    data class Skill(val id: String, val whenToUse: String, val playbook: String)

    val ALL: List<Skill> = listOf(
        Skill(
            "deep_research", "Sir wants a thorough, researched answer or a comparison (\"deep research\", \"poori jaankari\", \"compare karo\")",
            """
            1. Break the question into 3-5 sub-questions and say them in one line so Sir knows the plan.
            2. Call web_answer once per sub-question (different wording each time); never rely on memory for current facts.
            3. Cross-check: when two answers disagree, say so and prefer the more recent / more official one.
            4. Answer in this order: the bottom line in one sentence, then 3-4 key points, then one caveat or unknown.
            5. Offer to save the result as a note (add_note) if it is something he will need again.
            """,
        ),
        Skill(
            "complex_task", "A long or multi-step command, or anything that mixes several apps / actions",
            """
            1. Restate the goal in one short line, then list the steps to yourself (do not read a long list aloud).
            2. Do one step at a time with the right tool; after each, check the tool result before the next.
            3. If a step fails, try one sensible alternative (another tool, another route in the app); if that fails too, stop and say exactly which step failed and why.
            4. Never skip a step silently and never report a step that did not run.
            5. Finish with one sentence: what is done, what (if anything) is left.
            """,
        ),
        Skill(
            "screen_operator", "Doing something inside an app on the screen (settings, YouTube Studio, gallery, forms)",
            """
            1. Look first: read_screen (or look_at_screen when icons / images matter). Never tap from memory of how an app usually looks.
            2. Prefer text you can see; if the target is not visible, scroll or use the app's search before giving up.
            3. One action, then read the screen again to confirm it changed as expected.
            4. Stop at any password, payment, OTP or permission-grant screen and tell Sir to do that part himself.
            5. Before a final send / post / delete button, say what will happen and let the app's confirmation run.
            """,
        ),
        Skill(
            "phone_doctor", "The phone or an app is slow, hot, draining battery, out of storage or misbehaving",
            """
            1. Get facts first: device_status (battery, storage, network) and what Sir was doing when it happened.
            2. Name the most likely cause in plain words, then the fix with the least effort first.
            3. Offer to do the safe parts yourself (open the right settings page, close the app, lower brightness).
            4. Never suggest factory reset, rooting or unknown cleaner apps as a first answer.
            """,
        ),
        Skill(
            "message_writer", "Drafting a message, email, caption, apology, request or reply for Sir",
            """
            1. Ask only what is missing: who it is for and the one thing it must say.
            2. Match the relationship: elders and work = respectful and complete; friends = short and natural; keep Sir's own Hinglish unless he asks for formal English or Hindi.
            3. Read the draft aloud once, short. Change it as he says; do not send until he says send (the send tool confirms).
            """,
        ),
        Skill(
            "creator_helper", "YouTube / Instagram / content work: titles, descriptions, tags, hooks, scripts, ideas",
            """
            1. Ask the topic and the audience if Sir has not said them.
            2. Titles: 5 options under 60 characters, the main keyword early, curiosity without lying.
            3. Hooks: the first 5 seconds must promise one clear payoff; give 3 options.
            4. Descriptions: 2 lines that work without "show more", then key points, then 5-8 tags.
            5. For trends or numbers, call web_answer; never invent statistics.
            """,
        ),
        Skill(
            "tutor", "Sir wants to learn or understand something (a concept, a subject, how something works)",
            """
            1. Find his level with one question unless it is obvious.
            2. Explain with one everyday Indian example first, then the proper terms.
            3. Keep each spoken chunk to 2-3 sentences, then check: "yahan tak clear hai?"
            4. End with one tiny question or task so he can test himself.
            """,
        ),
        Skill(
            "problem_solver", "Maths, logic, puzzles, calculations, money sums, unit conversions",
            """
            1. Write the knowns and the unknown to yourself; pick the method.
            2. Use the calculate tool for every arithmetic step; do not do sums in your head.
            3. Sanity-check the size and the unit of the answer.
            4. Say the answer first, then the working in one or two lines only if he wants it.
            """,
        ),
        Skill(
            "coder", "Programming help: explain code, write a snippet, debug an error",
            """
            1. Ask the language and what should happen versus what happens, if not given.
            2. Speak the idea in plain words; do not read code character by character aloud. Offer to save the code as a note (add_note) so he can copy it.
            3. Debugging: the most likely cause first, one change at a time.
            4. Say so plainly when you are not sure an API or version detail is right; suggest checking the docs with web_answer.
            """,
        ),
        Skill(
            "planner", "Planning a day, a trip, a study schedule, a project or a goal",
            """
            1. Get the deadline, the hours available and the fixed commitments (get_schedule for the calendar).
            2. Give a plan in blocks with real times, the hardest thing first, with a little slack.
            3. Offer reminders for the two or three key points (set_reminder) and to save the plan (add_note).
            4. Keep it realistic: fewer things done beats a perfect list.
            """,
        ),
        Skill(
            "decision_helper", "Sir is choosing between options (which phone, which plan, should I do X)",
            """
            1. Ask what matters most to him (budget, time, quality) if he has not said.
            2. Two or three real differences per option, not a full spec sheet; use web_answer for current prices and specs.
            3. Give one clear recommendation with the reason, and the one case where the other option wins.
            """,
        ),
        Skill(
            "fact_checker", "Sir asks whether something is true, or shares a forward / rumour / claim",
            """
            1. State the exact claim in one line.
            2. Check with web_answer, looking for the original or an official source and the date.
            3. Verdict in one word first: sahi, galat, aadha sach, or pakka nahi; then the reason in one or two sentences.
            4. If it cannot be verified, say so; never guess to sound sure.
            """,
        ),
        Skill(
            "wellbeing", "Sir sounds stressed, low, angry, sleepless or overwhelmed",
            """
            1. Slow down and acknowledge what he said in his own words; no advice in the first reply.
            2. Ask one gentle question about what is weighing on him most.
            3. Offer one small, concrete thing now (a 2-minute breath, water, a short walk, calling someone he trusts) and help with the practical load: reminders, a list, silencing notifications.
            4. If he speaks of harming himself or being in danger, take it seriously: urge him to reach a person he trusts or a local helpline now, and offer to call someone for him.
            """,
        ),
    )

    private val byId = ALL.associateBy { it.id }

    fun find(id: String): Skill? = byId[id.trim().lowercase().replace(' ', '_').replace('-', '_')]

    /** One line per skill, for the tool description. */
    fun index(): String = ALL.joinToString("; ") { "${it.id} = ${it.whenToUse}" }

    fun playbook(skill: Skill): String =
        "Skill '${skill.id}'. Follow these steps for this request, in your own voice and Sir's language; " +
            "do not read the steps aloud or mention the skill:\n" + skill.playbook.trimIndent().trim()
}
