package com.adesh.devjarvis.brain

/**
 * JARVIS ke "skills": kisi khaas tarah ke kaam ka pakka tareeka. Brain `use_skill` tool se sahi skill ka
 * playbook maangta hai aur usi ke hisaab se kaam karta hai. Saare playbooks is app ke liye likhe gaye hain.
 * Pure, unit-tested.
 */
object Skills {
    data class Skill(val id: String, val whenToUse: String, val playbook: String, val area: String = "core")

    /** The big everyday skills, listed in full in the use_skill tool description. */
    val CORE: List<Skill> = listOf(
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
            1. Look first: read_screen gives every element a number (look_at_screen shows the numbered picture when icons / images matter). Never tap from memory of how an app usually looks.
            2. Act by number. If the target is not listed, scroll or use the app's search (type_text with submit=true) before giving up.
            3. Every action returns the new screen: read it to confirm the step worked before the next one. If it did not, try one other way, not the same tap again.
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
        Skill(
            "youtube_play", "Play a song or video on YouTube / YouTube Music",
            """
            1. search_in_app with app=youtube (or youtube_music for songs) and Sir's exact words.
            2. Then read_screen and tap the first real result (skip ads / 'Sponsored').
            3. If he named a channel or 'official', prefer that result.
            4. Confirm in one line what is playing.
            """,
        ),
        Skill(
            "whatsapp_message", "Send a WhatsApp message or reply to a chat",
            """
            1. send_whatsapp with the contact and the exact text (compose the text yourself if Sir only gave the gist).
            2. If the contact is unclear, ask which one using the names the tool offers.
            3. For a reply to the latest message, read_notifications first, then reply_to_notification.
            4. Never send without the app's confirmation.
            """,
        ),
        Skill(
            "call_flow", "Calling someone",
            """
            1. make_call with just the name; urgent=true only when Sir says emergency / turant.
            2. If the contact is not found, offer the closest names the tool returns.
            3. For an unknown number, read the digits back once before calling.
            """,
        ),
        Skill(
            "sms_flow", "Sending an SMS",
            """
            1. send_sms with contact and the exact text; keep it short and polite.
            2. For several people, one call per person.
            3. Report who received it.
            """,
        ),
        Skill(
            "alarm_timer", "Alarms, timers, wake-ups",
            """
            1. set_alarm for a clock time, set_timer for a duration, create_reminder for a message at a time.
            2. Read the time back in one line ("Kal subah 6:30 ka alarm laga diya").
            3. For 'har roz' use add_trigger with event=time instead.
            """,
        ),
        Skill(
            "calendar_day", "What is on today / tomorrow, add an event",
            """
            1. get_schedule for the day asked.
            2. To add, add_calendar_event with title, date, time; ask only for the missing piece.
            3. Summarise in one or two lines, earliest first.
            """,
        ),
        Skill(
            "weather_plan", "Weather and what to wear / carry",
            """
            1. weather for the city (home city if none).
            2. Give temperature, rain chance and one practical tip (umbrella, jacket, water).
            3. For trips, also the destination's weather.
            """,
        ),
        Skill(
            "news_brief", "News, headlines, what is happening",
            """
            1. web_answer with a precise query (topic + today's date context).
            2. Three headlines max, one line each, newest first.
            3. Say the source type (e.g. 'news reports') and never invent numbers.
            """,
        ),
        Skill(
            "cricket_score", "Live score or match result",
            """
            1. web_answer for the live score of the named match / team.
            2. Score, overs, who needs what, in one line.
            3. If no match is live, say when the next one is.
            """,
        ),
        Skill(
            "price_check", "Price of a product, fuel, gold, a stock or crypto",
            """
            1. web_answer with the exact item and city / exchange.
            2. Give the number with the date it applies to.
            3. For shopping, search_in_app on Amazon or Flipkart if Sir wants to buy (never pay).
            """,
        ),
        Skill(
            "translate_text", "Translate a sentence or a word",
            """
            1. Translate directly and say the result once, clearly.
            2. For a live two-person conversation, interpreter_mode on.
            3. Offer the pronunciation slowly if Sir asks.
            """,
        ),
        Skill(
            "math_quick", "Quick sums, percentages, unit and currency conversion",
            """
            1. calculate for every arithmetic step.
            2. For currency, web_answer for today's rate, then calculate.
            3. Answer first, working only if asked.
            """,
        ),
        Skill(
            "health_info", "Health questions, symptoms, medicines",
            """
            1. Give general, careful information in plain words; no diagnosis.
            2. Name the warning signs that need a doctor now.
            3. For emergencies (chest pain, breathlessness, heavy bleeding) say: call 112 / go to hospital now, and offer to call someone.
            """,
        ),
        Skill(
            "fitness_coach", "Exercise, diet, steps, water reminders",
            """
            1. Ask the goal and any injury once.
            2. Give a short, safe plan for today (sets / minutes / food).
            3. Offer reminders (create_reminder) and log progress with log_habit.
            """,
        ),
        Skill(
            "study_helper", "Studying, exams, homework, revision",
            """
            1. Find the topic and exam date.
            2. Explain the core idea simply, then 3 likely questions.
            3. Offer a revision plan with reminders (planner skill).
            """,
        ),
        Skill(
            "english_coach", "Improve English, correct a sentence, interview practice",
            """
            1. Correct the sentence and say why in one line.
            2. Give a more natural version.
            3. For practice, ask one question at a time and give short feedback.
            """,
        ),
        Skill(
            "job_career", "Resume, interview, job search, career choice",
            """
            1. Ask the role and experience level.
            2. Give concrete points (skills to highlight, questions to prepare).
            3. Offer to draft text with message_writer or save notes.
            """,
        ),
        Skill(
            "money_manager", "Spending, budget, savings",
            """
            1. expense_summary for the period.
            2. Point out the biggest category and one simple saving idea.
            3. Never ask for bank details; never pay.
            """,
        ),
        Skill(
            "shopping_list", "Shopping or to-do lists",
            """
            1. list_add / list_get / list_remove with the list name.
            2. Read back the full list when asked.
            3. Offer a reminder when Sir will be at the shop (remind_at_place).
            """,
        ),
        Skill(
            "notes_keeper", "Remember a note, find a note",
            """
            1. add_note with Sir's words.
            2. get_notes to find; quote the matching note.
            3. For facts about Sir himself, remember instead.
            """,
        ),
        Skill(
            "memory_keeper", "Remember something about Sir, his people or preferences",
            """
            1. remember with one clear sentence.
            2. When asked what you know, recall and say it naturally.
            3. Forget on request with forget.
            """,
        ),
        Skill(
            "routine_builder", "Create a shortcut / routine (\"jab main X bolun to Y\")",
            """
            1. save_routine with a short name and ordered steps.
            2. Repeat the steps back in one line.
            3. For automatic ones (time, charger, Wi-Fi) use add_trigger.
            """,
        ),
        Skill(
            "automation_rules", "Automatic rules: when charger / Wi-Fi / earphones / a time / a message, do something",
            """
            1. add_trigger with the right event and the action in Sir's words.
            2. list_triggers to show, delete_trigger to remove.
            3. Remind that rules need always-on running.
            """,
        ),
        Skill(
            "teach_task", "Learn a task by watching Sir do it",
            """
            1. teach_start, say the one line it returns, stay silent.
            2. When Sir is back, teach_save with his name for it.
            3. Later run_taught when he says that name.
            """,
        ),
        Skill(
            "phone_settings", "Change a phone setting (Wi-Fi, Bluetooth, data, hotspot, location, brightness, volume, DND)",
            """
            1. set_toggle for on/off switches, set_volume / set_brightness for levels.
            2. For other settings, open_settings then read_screen and tap.
            3. Confirm the new state in one line.
            """,
        ),
        Skill(
            "app_control", "Open, close, install or uninstall apps",
            """
            1. open_app by name; close_current_app to close.
            2. install_app opens the Play Store page; read_screen and tap Install.
            3. uninstall_app asks Sir first.
            """,
        ),
        Skill(
            "photo_gallery", "Find, share, hide or delete photos",
            """
            1. open_app Gallery / Photos, then screen_operator steps.
            2. Use search inside the gallery when there is one.
            3. Deleting: show what will be deleted and let the app confirm.
            """,
        ),
        Skill(
            "screen_reader", "Read or explain what is on the screen",
            """
            1. read_screen for text; look_at_screen for images, charts, icons.
            2. Summarise the important part first.
            3. Never read OTPs, passwords or card numbers aloud.
            """,
        ),
        Skill(
            "camera_vision", "Identify an object, read text from the real world, help with what Sir points at",
            """
            1. look with Sir's question (one photo).
            2. For a continuous view, live_camera on (Live mode only).
            3. Read text exactly; say when it is unclear.
            """,
        ),
        Skill(
            "document_reader", "Explain a bill, letter, form or PDF",
            """
            1. Ask Sir to use the FILE button, or look at it with the camera.
            2. Say what it is, the key numbers / dates, and what to do.
            3. Never read account or card numbers aloud.
            """,
        ),
        Skill(
            "navigation", "Directions, nearby places, travel time",
            """
            1. search_in_app with app=maps and the destination or 'near me' query.
            2. Give the distance / time if Sir asks via web_answer.
            3. Offer to share location in a message only if he asks.
            """,
        ),
        Skill(
            "travel_planner", "Plan a trip: tickets, hotels, itinerary",
            """
            1. Ask dates, budget and who is travelling.
            2. Day-by-day plan with times; web_answer for current prices / timings.
            3. Offer reminders and a packing list (list_add).
            """,
        ),
        Skill(
            "cooking", "Recipes, what to cook with what is at home",
            """
            1. Ask what ingredients there are if unknown.
            2. Give a short recipe: ingredients, then steps in order, with times.
            3. Offer a timer for each step.
            """,
        ),
        Skill(
            "entertainment", "Movies, series, songs to watch or listen to",
            """
            1. Ask the mood or language if unclear.
            2. Recommend 2-3 with one line each; web_answer for what is new.
            3. Offer to open it (search_in_app on YouTube / Spotify).
            """,
        ),
        Skill(
            "social_media", "Instagram, posts, captions, reels",
            """
            1. creator_helper for captions and hashtags.
            2. To post or reply, screen_operator steps; the final post needs Sir's confirmation.
            3. Never post on Sir's behalf without his explicit command.
            """,
        ),
        Skill(
            "email_helper", "Write or reply to an email",
            """
            1. Draft with message_writer rules (formal when needed).
            2. Open the mail app with open_app; use screen steps to fill it.
            3. Sir sends it (confirmation).
            """,
        ),
        Skill(
            "reminder_smart", "Reminders that depend on place or time",
            """
            1. create_reminder for a time; remind_at_place for arriving somewhere (save_place first).
            2. Read back when / where it will fire.
            3. Confirm the exact time or place once, so a wrong reminder is caught now.
            """,
        ),
        Skill(
            "diary_recall", "What did I do on a day, what happened yesterday",
            """
            1. diary with the day.
            2. Summarise in two or three lines; mention notes or expenses from that day if relevant.
            3. If the diary has no entry for that day, say so and offer what the activity log shows.
            """,
        ),
        Skill(
            "habit_tracker", "Track habits (water, gym, study, medicine)",
            """
            1. log_habit when Sir says he did one.
            2. habit_summary when he asks how he is doing; encourage briefly.
            3. Offer a daily reminder (add_trigger time).
            """,
        ),
        Skill(
            "emergency", "Danger, accident, someone hurt, Sir says SOS",
            """
            1. emergency_sos only when Sir asks for SOS / madad bhejo.
            2. For calls say 112 and offer make_call urgent=true to family.
            3. Stay calm, short, practical.
            """,
        ),
        Skill(
            "device_care", "Battery, storage, heating, speed of the phone",
            """
            1. device_status for facts.
            2. Suggest the safest fix first (close heavy apps, lower brightness, clear big files).
            3. Offer to open the right settings page.
            """,
        ),
        Skill(
            "security_privacy", "Passwords, OTPs, scams, suspicious messages or calls",
            """
            1. Never read or store OTPs, passwords or card numbers.
            2. Explain the scam signs in the message and what to do (don't click, block, report).
            3. Offer to block / delete via the screen only on command.
            """,
        ),
        Skill(
            "legal_docs", "Aadhaar, PAN, passport, government forms",
            """
            1. Explain the steps and documents needed in plain words.
            2. web_answer for the official site and current fees.
            3. Never ask Sir to share numbers or OTPs with you.
            """,
        ),
        Skill(
            "kids_family", "Stories, homework help for kids, family planning",
            """
            1. Simple language, short sentences, kind tone.
            2. For stories, original ones with a gentle moral.
            3. For family events, use planner and reminders.
            """,
        ),
        Skill(
            "conversation_buddy", "Casual chat, jokes, boredom, small talk",
            """
            1. Be warm and witty in Sir's Hinglish, short turns.
            2. Ask one interesting question back.
            3. No lectures, no lists.
            """,
        ),
        Skill(
            "motivation", "Sir feels lazy, demotivated, needs a push",
            """
            1. Acknowledge briefly, then one concrete next step he can do in 5 minutes.
            2. Offer a timer or reminder.
            3. Encourage like a loyal friend, not a poster.
            """,
        ),
        Skill(
            "brainstorm", "Ideas for a business, video, name, gift, plan",
            """
            1. Ask the goal and constraint if unclear.
            2. Give 5 varied ideas in one line each, best first.
            3. Offer to expand the one he likes.
            """,
        ),
        Skill(
            "summarize", "Summarise an article, video, chat or long text",
            """
            1. Get the content (read_screen, FILE, or web_answer for a link / topic).
            2. Bottom line first, then 3 key points.
            3. Mention what was left out if important.
            """,
        ),
        Skill(
            "compare_products", "Compare phones, bikes, plans, apps",
            """
            1. web_answer for current specs and prices.
            2. Two or three differences that matter to Sir, then one clear pick.
            3. Note the one case where the other wins.
            """,
        ),
        Skill(
            "local_services", "Find a doctor, shop, mechanic, restaurant nearby",
            """
            1. search_in_app with app=maps and the query 'near me'.
            2. Read the top results with rating and distance from the screen.
            3. Offer to call or get directions.
            """,
        ),
        Skill(
            "time_date", "Time, date, day, countdown to a date",
            """
            1. get_time for now; calculate for days between dates.
            2. Answer in one line in Sir's language.
            3. For a countdown, give days first, then weeks if it is far away.
            """,
        ),
        Skill(
            "festival_wishes", "Wishes and messages for festivals, birthdays, anniversaries",
            """
            1. Write a short, warm, original message in Sir's tone (Hinglish unless asked).
            2. Offer to send via send_whatsapp to the person.
            3. Offer a reminder for next year's date (remember / create_reminder).
            """,
        ),
        Skill(
            "tech_support", "Help with any app or setting (\"ye kaise karte hain\")",
            """
            1. Explain the steps for this phone briefly.
            2. Offer to do it with screen_operator steps.
            3. If an app is broken, suggest clear cache / update before reinstall.
            """,
        ),
    )

    /** Core skills plus the 200+ skill library. */
    val ALL: List<Skill> by lazy { CORE + SkillLibrary.ALL }

    private val byId by lazy { ALL.associateBy { it.id } }

    fun find(id: String): Skill? = byId[id.trim().lowercase().replace(' ', '_').replace('-', '_')]

    /** One line per core skill, then the library areas with their ids, for the tool description. */
    fun index(): String = CORE.joinToString("; ") { "${it.id} = ${it.whenToUse}" }

    /** Library ids grouped by area, compact. */
    fun libraryIndex(): String = SkillLibrary.ALL.groupBy { it.area }.entries.joinToString("; ") { e ->
        e.key + ": " + e.value.joinToString(",") { it.id }
    }

    private val STOP = setOf("a", "an", "the", "to", "of", "for", "and", "or", "in", "on", "my", "me", "is", "it", "do", "how",
        "karo", "kar", "do", "de", "ka", "ki", "ke", "ko", "hai", "mujhe", "mera", "meri", "kya", "se", "me", "aur", "sir", "wants")

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 && it !in STOP }.map { it.trimEnd('s') }.toSet()

    private val wordsOf by lazy { ALL.associate { it.id to (words(it.id.replace('_', ' ')) to words(it.whenToUse)) } }

    /** Best skills for an id or a free-text request, best first. */
    fun search(query: String, limit: Int = 5): List<Skill> {
        find(query)?.let { return listOf(it) }
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        return ALL.map { skill ->
            val (idWords, whenWords) = wordsOf.getValue(skill.id)
            skill to (q.count { it in idWords } * 3 + q.count { it in whenWords } * 2 +
                q.count { w -> whenWords.any { it.length > 3 && (it.startsWith(w) || w.startsWith(it)) } })
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun playbook(skill: Skill): String =
        "Skill '${skill.id}'. Follow these steps for this request, in your own voice and Sir's language; " +
            "do not read the steps aloud or mention the skill:\n" + skill.playbook.trimIndent().trim()
}
