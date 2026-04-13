package com.privateai.camera.grammar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device grammar checker using regex-based rules + 4300+ misspelling dictionary.
 * Dictionary sourced from Wikipedia's common misspellings list (public domain)
 * plus SMS abbreviations.
 * Runs entirely in-process — no network, no third-party dependencies.
 */
class LocalGrammarChecker(context: Context) : GrammarChecker {

    private data class Rule(
        val id: String,
        val pattern: Regex,
        val message: (MatchResult) -> String,
        val suggest: (MatchResult) -> List<String>,
        val matchGroup: Int = 0
    )

    private val rules: List<Rule> = buildRules()
    private val misspellings: Map<String, String>

    init {
        // Load misspellings dictionary from assets (~120KB, 4300+ entries)
        misspellings = loadMisspellings(context)
    }

    override suspend fun check(text: String): List<GrammarError> = withContext(Dispatchers.Default) {
        val errors = mutableListOf<GrammarError>()
        // 1. Run regex-based grammar rules
        for (rule in rules) {
            rule.pattern.findAll(text).forEach { match ->
                val group = match.groups[rule.matchGroup] ?: return@forEach
                val suggestions = rule.suggest(match)
                if (suggestions.isNotEmpty()) {
                    errors.add(
                        GrammarError(
                            fromPos = group.range.first,
                            toPos = group.range.last + 1,
                            message = rule.message(match),
                            suggestions = suggestions,
                            ruleId = rule.id
                        )
                    )
                }
            }
        }

        // 2. Run dictionary-based misspelling check
        val wordPattern = Regex("\\b([a-zA-Z]+)\\b")
        wordPattern.findAll(text).forEach { match ->
            val word = match.groupValues[1]
            val lower = word.lowercase()
            // Skip valid single-letter English words
            if (lower == "a" || lower == "i") return@forEach
            val correction = misspellings[lower]
            if (correction != null) {
                // Check this range isn't already covered by a grammar rule
                val from = match.range.first
                val to = match.range.last + 1
                if (errors.none { it.fromPos < to && it.toPos > from }) {
                    // Preserve original case for single-word corrections
                    val suggestions = correction.split(",").map { it.trim() }.map { sug ->
                        if (word[0].isUpperCase() && sug.length > 1 && sug[0].isLowerCase())
                            sug.replaceFirstChar { it.uppercase() }
                        else sug
                    }
                    errors.add(
                        GrammarError(
                            fromPos = from,
                            toPos = to,
                            message = if (suggestions.size == 1)
                                "Did you mean \"${suggestions[0]}\"?"
                            else
                                "Did you mean: ${suggestions.joinToString(" or ") { "\"$it\"" }}?",
                            suggestions = suggestions,
                            ruleId = "MISSPELLING"
                        )
                    )
                }
            }
        }

        // Deduplicate overlapping errors — keep the first one
        dedup(errors)
    }

    private fun dedup(errors: List<GrammarError>): List<GrammarError> {
        val sorted = errors.sortedBy { it.fromPos }
        val result = mutableListOf<GrammarError>()
        var lastEnd = -1
        for (e in sorted) {
            if (e.fromPos >= lastEnd) {
                result.add(e)
                lastEnd = e.toPos
            }
        }
        return result
    }

    companion object {
        private val I = RegexOption.IGNORE_CASE

        /** Remove third-person -s/-es to get base form */
        private fun deconjugate(verb: String): String {
            val v = verb.lowercase()
            return when {
                v == "goes" -> "go"
                v == "does" -> "do"
                v == "tries" -> "try"
                v == "flies" -> "fly"
                v.endsWith("ies") -> v.removeSuffix("ies") + "y"
                v.endsWith("ches") || v.endsWith("shes") || v.endsWith("sses") || v.endsWith("xes") || v.endsWith("zes") -> v.removeSuffix("es")
                v.endsWith("ves") -> v.removeSuffix("ves") + "ve"  // leaves→leave, drives→drive
                v.endsWith("ses") -> v.removeSuffix("s")  // closes→close, loses→lose
                v.endsWith("s") -> v.removeSuffix("s")
                else -> v
            }
        }

        private val PAST_TO_BASE = mapOf(
            "knew" to "know", "went" to "go", "came" to "come", "gave" to "give",
            "took" to "take", "made" to "make", "saw" to "see", "found" to "find",
            "told" to "tell", "sent" to "send", "got" to "get", "kept" to "keep",
            "brought" to "bring", "met" to "meet", "spent" to "spend", "lost" to "lose",
            "caught" to "catch", "broke" to "break", "chose" to "choose", "spoke" to "speak",
            "drove" to "drive", "fell" to "fall", "paid" to "pay", "wrote" to "write",
            "ate" to "eat", "ran" to "run", "felt" to "feel", "left" to "leave",
            "woke" to "wake", "forgot" to "forget", "thought" to "think", "said" to "say",
            "heard" to "hear", "began" to "begin", "became" to "become", "built" to "build",
            "bought" to "buy", "drew" to "draw", "drank" to "drink", "flew" to "fly",
            "grew" to "grow", "held" to "hold", "hid" to "hide", "hurt" to "hurt",
            "led" to "lead", "lent" to "lend", "lit" to "light", "meant" to "mean",
            "put" to "put", "quit" to "quit", "rode" to "ride", "rose" to "rise",
            "sang" to "sing", "sat" to "sit", "set" to "set", "shook" to "shake",
            "shot" to "shoot", "showed" to "show", "shut" to "shut", "slept" to "sleep",
            "slid" to "slide", "stood" to "stand", "stole" to "steal", "stuck" to "stick",
            "struck" to "strike", "swam" to "swim", "swore" to "swear", "swept" to "sweep",
            "taught" to "teach", "threw" to "throw", "understood" to "understand",
            "wore" to "wear", "won" to "win", "wound" to "wind"
        )

        /**
         * Load misspellings from assets/misspellings.txt
         * Format: misspelling->correction (one per line)
         * Multiple corrections separated by commas: misspelling->correction1, correction2
         */
        private fun loadMisspellings(context: Context): Map<String, String> {
            return try {
                val map = HashMap<String, String>(5000)
                context.assets.open("misspellings.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("->", limit = 2)
                        if (parts.size == 2) {
                            val typo = parts[0].trim().lowercase()
                            val correction = parts[1].trim()
                            if (typo.isNotEmpty() && correction.isNotEmpty()) {
                                map[typo] = correction
                            }
                        }
                    }
                }
                map
            } catch (_: Exception) {
                emptyMap()
            }
        }

        private fun buildRules(): List<Rule> = listOf(
            // --- Double words ---
            Rule(
                id = "DOUBLE_WORD",
                pattern = Regex("\\b(\\w+)\\s+\\1\\b", I),
                message = { "Repeated word: \"${it.groupValues[1]}\"" },
                suggest = { listOf(it.groupValues[1]) }
            ),

            // --- A / An ---
            Rule(
                id = "A_AN_VOWEL",
                pattern = Regex("\\b(a)\\s+(a|e|i|o|u|ho|he)\\w*\\b", I),
                message = { "Use \"an\" before a vowel sound" },
                suggest = { listOf("an") },
                matchGroup = 1
            ),
            Rule(
                id = "AN_CONSONANT",
                pattern = Regex("\\b(an)\\s+(?!un|up|us|ut|ho|he|ha|hi|hu|hr|hy|11|18|8)([bcdfgjklmnpqrstvwxyz]\\w*)\\b", I),
                message = { "Use \"a\" before a consonant sound" },
                suggest = { listOf("a") },
                matchGroup = 1
            ),

            // --- Their / There / They're ---
            Rule(
                id = "THERE_POSSESSIVE",
                pattern = Regex("\\b(there)\\s+(car|house|dog|cat|phone|name|book|child|children|family|friend|parents|mother|father|sister|brother|idea|plan|home|room|work|job|team|company|school)s?\\b", I),
                message = { "Did you mean \"their\" (possessive)?" },
                suggest = { listOf("their") },
                matchGroup = 1
            ),
            Rule(
                id = "THEIR_LOCATION",
                pattern = Regex("\\b(their)\\s+(is|are|was|were|will be|has been|have been|should be|could be|would be|might be)\\b", I),
                message = { "Did you mean \"there\" (location/existence)?" },
                suggest = { listOf("there") },
                matchGroup = 1
            ),

            // --- Your / You're ---
            Rule(
                id = "YOUR_CONTRACTION",
                pattern = Regex("\\b(your)\\s+(welcome|right|wrong|sure|going|doing|being|looking|coming|leaving|making|getting|trying|having|saying|telling|kidding|joking)\\b", I),
                message = { "Did you mean \"you're\" (you are)?" },
                suggest = { listOf("you're") },
                matchGroup = 1
            ),

            // --- Its / It's ---
            Rule(
                id = "ITS_CONTRACTION",
                pattern = Regex("\\b(its)\\s+(a|an|the|not|been|going|very|too|quite|really|so|just|about|time|important|clear|true|possible|necessary|better|worse|great|nice|good|bad|hard|easy|difficult)\\b", I),
                message = { "Did you mean \"it's\" (it is)?" },
                suggest = { listOf("it's") },
                matchGroup = 1
            ),

            // --- Then / Than ---
            Rule(
                id = "THEN_COMPARISON",
                pattern = Regex("\\b(more|less|better|worse|bigger|smaller|faster|slower|higher|lower|greater|fewer|larger|older|newer|easier|harder|longer|shorter|taller|stronger|weaker|cheaper|richer|smarter)\\s+(then)\\b", I),
                message = { "Use \"than\" for comparisons" },
                suggest = { listOf("than") },
                matchGroup = 2
            ),

            // --- Affect / Effect ---
            Rule(
                id = "AFFECT_NOUN",
                pattern = Regex("\\b(the|a|an|no|this|that|its|positive|negative|big|huge|great|little|small|side|main)\\s+(affect)\\b", I),
                message = { "Did you mean \"effect\" (noun)?" },
                suggest = { listOf("effect") },
                matchGroup = 2
            ),

            // --- Lose / Loose ---
            Rule(
                id = "LOOSE_VERB",
                pattern = Regex("\\b(will|might|could|would|should|can|may|don't|didn't|won't|cannot|to|gonna|going to)\\s+(loose)\\b", I),
                message = { "Did you mean \"lose\" (verb)?" },
                suggest = { listOf("lose") },
                matchGroup = 2
            ),

            // --- Subject-verb agreement ---
            Rule(
                id = "HE_DONT",
                pattern = Regex("\\b(he|she|it)\\s+(don't)\\b", I),
                message = { "Use \"doesn't\" with ${it.groupValues[1]}" },
                suggest = { listOf("doesn't") },
                matchGroup = 2
            ),
            Rule(
                id = "THEY_WAS",
                pattern = Regex("\\b(they|we)\\s+(was)\\b", I),
                message = { "Use \"were\" with ${it.groupValues[1]}" },
                suggest = { listOf("were") },
                matchGroup = 2
            ),
            Rule(
                id = "I_HAS",
                pattern = Regex("\\b(I)\\s+(has)\\b"),
                message = { "Use \"have\" with \"I\"" },
                suggest = { listOf("have") },
                matchGroup = 2
            ),

            // --- he/she/it + have (not auxiliary "have been") ---
            Rule(
                id = "HE_HAVE",
                pattern = Regex("\\b(he|she|it)\\s+(have)\\s+(?!been|to)\\b", I),
                message = { "Use \"has\" with ${it.groupValues[1]}" },
                suggest = { listOf("has") },
                matchGroup = 2
            ),

            // --- singular noun + were ---
            Rule(
                id = "SINGULAR_WERE",
                pattern = Regex("\\b(boss|teacher|doctor|manager|friend|mother|father|sister|brother|child|person|man|woman|girl|boy|baby|dog|cat|car|house|it|he|she)\\s+(were)\\b", I),
                message = { "Use \"was\" with \"${it.groupValues[1]}\"" },
                suggest = { listOf("was") },
                matchGroup = 2
            ),

            // --- he/she/it + base form irregular verbs (should be past tense) ---
            // Pattern: "she go" → "she went", "he say" → "he said"
            Rule(
                id = "SHE_GO",
                pattern = Regex("\\b(he|she|it|I)\\s+(go)\\s+(?!on|ahead)\\b", I),
                message = { "Did you mean \"went\" or \"goes\"?" },
                suggest = { listOf("went", "goes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_SAY",
                pattern = Regex("\\b(he|she|it|I)\\s+(say)\\s", I),
                message = { "Did you mean \"said\" or \"says\"?" },
                suggest = { listOf("said", "says") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_COME",
                pattern = Regex("\\b(he|she|it)\\s+(come)\\s+(?!to|from|back|on|in|up|and)\\b", I),
                message = { "Did you mean \"came\" or \"comes\"?" },
                suggest = { listOf("came", "comes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_GIVE",
                pattern = Regex("\\b(he|she|it|I)\\s+(give)\\s", I),
                message = { "Did you mean \"gave\" or \"gives\"?" },
                suggest = { listOf("gave", "gives") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_TAKE",
                pattern = Regex("\\b(he|she|it|I)\\s+(take)\\s+(?!a|the|care|place|off|on|out|up|and)\\b", I),
                message = { "Did you mean \"took\" or \"takes\"?" },
                suggest = { listOf("took", "takes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_MAKE",
                pattern = Regex("\\b(he|she|it|I)\\s+(make)\\s", I),
                message = { "Did you mean \"made\" or \"makes\"?" },
                suggest = { listOf("made", "makes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_KNOW",
                pattern = Regex("\\b(he|she|it)\\s+(know)\\s", I),
                message = { "Did you mean \"knew\" or \"knows\"?" },
                suggest = { listOf("knew", "knows") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_SEE",
                pattern = Regex("\\b(he|she|it|I)\\s+(see)\\s+(?!you|the|a|if|that)\\b", I),
                message = { "Did you mean \"saw\" or \"sees\"?" },
                suggest = { listOf("saw", "sees") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_LOOK",
                pattern = Regex("\\b(he|she|it)\\s+(look)\\s", I),
                message = { "Did you mean \"looked\" or \"looks\"?" },
                suggest = { listOf("looked", "looks") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_THINK",
                pattern = Regex("\\b(he|she|it)\\s+(think)\\s", I),
                message = { "Did you mean \"thought\" or \"thinks\"?" },
                suggest = { listOf("thought", "thinks") },
                matchGroup = 2
            ),

            // --- he/she + base form regular verbs (need -s or -ed) ---
            // Common verbs that need third-person-s or past tense
            Rule(
                id = "SHE_WAKE",
                pattern = Regex("\\b(he|she|it|I)\\s+(wake)\\s+up\\b", I),
                message = { "Did you mean \"woke up\" or \"wakes up\"?" },
                suggest = { listOf("woke", "wakes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_FORGET",
                pattern = Regex("\\b(he|she|it|I)\\s+(forget)\\s", I),
                message = { "Did you mean \"forgot\" or \"forgets\"?" },
                suggest = { listOf("forgot", "forgets") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_ARRIVE",
                pattern = Regex("\\b(he|she|it|I)\\s+(arrive)\\b", I),
                message = { "Did you mean \"arrived\" or \"arrives\"?" },
                suggest = { listOf("arrived", "arrives") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_LEAVE",
                pattern = Regex("\\b(he|she|it|I)\\s+(leave)\\s+(?!me|it|the|a)\\b", I),
                message = { "Did you mean \"left\" or \"leaves\"?" },
                suggest = { listOf("left", "leaves") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_WANT",
                pattern = Regex("\\b(he|she|it)\\s+(want)\\s", I),
                message = { "Use \"wants\" with ${it.groupValues[1]}" },
                suggest = { listOf("wants") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_NEED",
                pattern = Regex("\\b(he|she|it)\\s+(need)\\s", I),
                message = { "Use \"needs\" with ${it.groupValues[1]}" },
                suggest = { listOf("needs") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_LIKE",
                pattern = Regex("\\b(he|she|it)\\s+(like)\\s", I),
                message = { "Use \"likes\" with ${it.groupValues[1]}" },
                suggest = { listOf("likes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_WORK",
                pattern = Regex("\\b(he|she|it)\\s+(work)\\s", I),
                message = { "Use \"works\" with ${it.groupValues[1]}" },
                suggest = { listOf("works") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_LIVE",
                pattern = Regex("\\b(he|she|it)\\s+(live)\\s", I),
                message = { "Use \"lives\" with ${it.groupValues[1]}" },
                suggest = { listOf("lives") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_START",
                pattern = Regex("\\b(he|she|it)\\s+(start)\\s", I),
                message = { "Use \"starts\" with ${it.groupValues[1]}" },
                suggest = { listOf("starts") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_FEEL",
                pattern = Regex("\\b(he|she|it)\\s+(feel)\\s", I),
                message = { "Did you mean \"felt\" or \"feels\"?" },
                suggest = { listOf("felt", "feels") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_WRITE",
                pattern = Regex("\\b(he|she|it|I)\\s+(write)\\s", I),
                message = { "Did you mean \"wrote\" or \"writes\"?" },
                suggest = { listOf("wrote", "writes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_RUN",
                pattern = Regex("\\b(he|she|it)\\s+(run)\\s+(?!a|the)\\b", I),
                message = { "Did you mean \"ran\" or \"runs\"?" },
                suggest = { listOf("ran", "runs") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_EAT",
                pattern = Regex("\\b(he|she|it|I)\\s+(eat)\\s", I),
                message = { "Did you mean \"ate\" or \"eats\"?" },
                suggest = { listOf("ate", "eats") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_TELL",
                pattern = Regex("\\b(he|she|it|I)\\s+(tell)\\s", I),
                message = { "Did you mean \"told\" or \"tells\"?" },
                suggest = { listOf("told", "tells") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_FIND",
                pattern = Regex("\\b(he|she|it|I)\\s+(find)\\s", I),
                message = { "Did you mean \"found\" or \"finds\"?" },
                suggest = { listOf("found", "finds") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_SEND",
                pattern = Regex("\\b(he|she|it|I)\\s+(send)\\s", I),
                message = { "Did you mean \"sent\" or \"sends\"?" },
                suggest = { listOf("sent", "sends") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_GET",
                pattern = Regex("\\b(he|she|it)\\s+(get)\\s+(?!to|a|the|up|out|in|on|off|back|ready)\\b", I),
                message = { "Did you mean \"got\" or \"gets\"?" },
                suggest = { listOf("got", "gets") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_KEEP",
                pattern = Regex("\\b(he|she|it)\\s+(keep)\\s", I),
                message = { "Did you mean \"kept\" or \"keeps\"?" },
                suggest = { listOf("kept", "keeps") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_BRING",
                pattern = Regex("\\b(he|she|it|I)\\s+(bring)\\s", I),
                message = { "Did you mean \"brought\" or \"brings\"?" },
                suggest = { listOf("brought", "brings") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_MEET",
                pattern = Regex("\\b(he|she|it|I)\\s+(meet)\\s", I),
                message = { "Did you mean \"met\" or \"meets\"?" },
                suggest = { listOf("met", "meets") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_SPEND",
                pattern = Regex("\\b(he|she|it|I)\\s+(spend)\\s", I),
                message = { "Did you mean \"spent\" or \"spends\"?" },
                suggest = { listOf("spent", "spends") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_LOSE",
                pattern = Regex("\\b(he|she|it|I)\\s+(lose)\\s", I),
                message = { "Did you mean \"lost\" or \"loses\"?" },
                suggest = { listOf("lost", "loses") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_CATCH",
                pattern = Regex("\\b(he|she|it|I)\\s+(catch)\\s", I),
                message = { "Did you mean \"caught\" or \"catches\"?" },
                suggest = { listOf("caught", "catches") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_BREAK",
                pattern = Regex("\\b(he|she|it|I)\\s+(break)\\s", I),
                message = { "Did you mean \"broke\" or \"breaks\"?" },
                suggest = { listOf("broke", "breaks") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_CHOOSE",
                pattern = Regex("\\b(he|she|it|I)\\s+(choose)\\s", I),
                message = { "Did you mean \"chose\" or \"chooses\"?" },
                suggest = { listOf("chose", "chooses") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_SPEAK",
                pattern = Regex("\\b(he|she|it|I)\\s+(speak)\\s", I),
                message = { "Did you mean \"spoke\" or \"speaks\"?" },
                suggest = { listOf("spoke", "speaks") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_DRIVE",
                pattern = Regex("\\b(he|she|it|I)\\s+(drive)\\s", I),
                message = { "Did you mean \"drove\" or \"drives\"?" },
                suggest = { listOf("drove", "drives") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_FALL",
                pattern = Regex("\\b(he|she|it|I)\\s+(fall)\\s", I),
                message = { "Did you mean \"fell\" or \"falls\"?" },
                suggest = { listOf("fell", "falls") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_PAY",
                pattern = Regex("\\b(he|she|it)\\s+(pay)\\s", I),
                message = { "Did you mean \"paid\" or \"pays\"?" },
                suggest = { listOf("paid", "pays") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_READ_BASE",
                pattern = Regex("\\b(he|she|it)\\s+(read)\\s+(?!the|a|it|this|that|more)\\b", I),
                message = { "Use \"reads\" with ${it.groupValues[1]}" },
                suggest = { listOf("reads") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_PLAY",
                pattern = Regex("\\b(he|she|it)\\s+(play)\\s", I),
                message = { "Use \"plays\" with ${it.groupValues[1]}" },
                suggest = { listOf("plays") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_CALL",
                pattern = Regex("\\b(he|she|it)\\s+(call)\\s", I),
                message = { "Use \"calls\" with ${it.groupValues[1]}" },
                suggest = { listOf("calls") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_TRY",
                pattern = Regex("\\b(he|she|it)\\s+(try)\\s", I),
                message = { "Use \"tries\" with ${it.groupValues[1]}" },
                suggest = { listOf("tries") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_MOVE",
                pattern = Regex("\\b(he|she|it)\\s+(move)\\s", I),
                message = { "Use \"moves\" with ${it.groupValues[1]}" },
                suggest = { listOf("moves") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_HELP",
                pattern = Regex("\\b(he|she|it)\\s+(help)\\s", I),
                message = { "Use \"helps\" with ${it.groupValues[1]}" },
                suggest = { listOf("helps") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_WALK",
                pattern = Regex("\\b(he|she|it)\\s+(walk)\\s", I),
                message = { "Use \"walks\" with ${it.groupValues[1]}" },
                suggest = { listOf("walks") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_OPEN",
                pattern = Regex("\\b(he|she|it)\\s+(open)\\s", I),
                message = { "Use \"opens\" with ${it.groupValues[1]}" },
                suggest = { listOf("opens") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_CLOSE",
                pattern = Regex("\\b(he|she|it)\\s+(close)\\s", I),
                message = { "Use \"closes\" with ${it.groupValues[1]}" },
                suggest = { listOf("closes") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_ASK",
                pattern = Regex("\\b(he|she|it)\\s+(ask)\\s", I),
                message = { "Use \"asks\" with ${it.groupValues[1]}" },
                suggest = { listOf("asks") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_STOP",
                pattern = Regex("\\b(he|she|it)\\s+(stop)\\s", I),
                message = { "Use \"stops\" with ${it.groupValues[1]}" },
                suggest = { listOf("stops") },
                matchGroup = 2
            ),
            Rule(
                id = "SHE_WAIT",
                pattern = Regex("\\b(he|she|it)\\s+(wait)\\s", I),
                message = { "Use \"waits\" with ${it.groupValues[1]}" },
                suggest = { listOf("waits") },
                matchGroup = 2
            ),

            // --- "I" + verb with -s/-es (I feels → I feel) ---
            Rule(
                id = "I_VERBS",
                pattern = Regex("\\bI\\s+(feels|says|goes|arrives|leaves|knows|looks|thinks|wants|needs|likes|works|lives|starts|plays|calls|tries|moves|helps|walks|opens|closes|asks|stops|waits|comes|gives|takes|makes|runs|eats|writes|tells|finds|sends|gets|keeps|brings|meets|spends|loses|catches|breaks|falls|pays|reads|drives|speaks|chooses)\\b", I),
                message = { "Use \"${deconjugate(it.groupValues[1])}\" with \"I\"" },
                suggest = { listOf(deconjugate(it.groupValues[1])) },
                matchGroup = 1
            ),

            // --- "didn't/don't/doesn't/won't/can't/shouldn't" + past tense → base form ---
            Rule(
                id = "DIDNT_PAST",
                pattern = Regex("\\b(didn't|didn.t)\\s+(knew|went|came|gave|took|made|saw|found|told|sent|got|kept|brought|met|spent|lost|caught|broke|chose|spoke|drove|fell|paid|wrote|ate|ran|felt|left|woke|forgot|thought|said|heard|began|became|built|bought|drew|drank|flew|grew|held|hid|hurt|led|lent|lit|meant|put|quit|rode|rose|sang|sat|set|shook|shot|showed|shut|slept|slid|stood|stole|stuck|struck|swam|swore|swept|taught|threw|understood|wore|won|wound)\\b", I),
                message = { "Use base form after \"didn't\": \"${PAST_TO_BASE[it.groupValues[2].lowercase()] ?: it.groupValues[2]}\"" },
                suggest = { listOf(PAST_TO_BASE[it.groupValues[2].lowercase()] ?: it.groupValues[2]) },
                matchGroup = 2
            ),

            // --- "don't" + verb with -s (don't listens → don't listen) ---
            Rule(
                id = "DONT_VERBS",
                pattern = Regex("\\b(don't|don.t|doesn.t|doesn't)\\s+(\\w+s)\\b", I),
                message = { "Use \"${deconjugate(it.groupValues[2])}\" after \"${it.groupValues[1]}\"" },
                suggest = { listOf(deconjugate(it.groupValues[2])) },
                matchGroup = 2
            ),

            // --- "should/would/could/might/will/can/may" + past tense → base form ---
            Rule(
                id = "MODAL_PAST",
                pattern = Regex("\\b(should|would|could|might|will|can|may|must)\\s+(came|went|knew|gave|took|made|saw|found|told|sent|got|kept|brought|met|spent|lost|caught|broke|chose|spoke|drove|fell|paid|wrote|ate|ran|felt|left|woke|forgot|thought|said|heard|began|became)\\b", I),
                message = { "Use base form after \"${it.groupValues[1]}\": \"${
                    PAST_TO_BASE[it.groupValues[2].lowercase()] ?: it.groupValues[2]
                }\"" },
                suggest = {
                    listOf(PAST_TO_BASE[it.groupValues[2].lowercase()] ?: it.groupValues[2])
                },
                matchGroup = 2
            ),

            // --- "would had" → "would have" ---
            Rule(
                id = "WOULD_HAD",
                pattern = Regex("\\b(would|could|should|might|must)\\s+(had)\\s+(been|done|gone|come|made|seen|taken|given|known|found)\\b", I),
                message = { "Use \"have\" after \"${it.groupValues[1]}\"" },
                suggest = { listOf("have") },
                matchGroup = 2
            ),

            // --- "people/they/we" + "was" → "were" ---
            Rule(
                id = "PLURAL_WAS",
                pattern = Regex("\\b(people|children|men|women|students|workers|players|customers|employees|members)\\s+(?:there\\s+)?(was)\\b", I),
                message = { "Use \"were\" with \"${it.groupValues[1]}\"" },
                suggest = { listOf("were") },
                matchGroup = 2
            ),

            // --- "buying/doing nothing" → "anything" (double negative after negative context) ---
            Rule(
                id = "DOUBLE_NEGATIVE",
                pattern = Regex("\\b(without|not|never|don't|didn't|won't|can't|couldn't|shouldn't|wouldn't|isn't|wasn't|haven't|hasn't)\\s+\\w+\\s+(nothing)\\b", I),
                message = { "Use \"anything\" instead of \"nothing\" after a negative" },
                suggest = { listOf("anything") },
                matchGroup = 2
            ),

            // --- "advices" → "advice" (uncountable nouns) ---
            Rule(
                id = "UNCOUNTABLE",
                pattern = Regex("\\b(advices|informations|furnitures|equipments|luggages|baggages|homeworks|researches|knowledges|evidences)\\b", I),
                message = { "\"${it.groupValues[1].removeSuffix("s")}\" is uncountable — no plural form" },
                suggest = { listOf(it.groupValues[1].lowercase().removeSuffix("s")) },
                matchGroup = 1
            ),

            // --- Capitalization after sentence-ending punctuation ---
            Rule(
                id = "CAPITALIZE_AFTER_PERIOD",
                pattern = Regex("[.!?]\\s+([a-z])"),
                message = { "Capitalize the first word of a sentence" },
                suggest = { listOf(it.groupValues[1].uppercase()) },
                matchGroup = 1
            ),

            // --- Space before punctuation ---
            Rule(
                id = "SPACE_BEFORE_PUNCT",
                pattern = Regex("\\w(\\s+)[,.]"),
                message = { "Remove space before punctuation" },
                suggest = { listOf("") },
                matchGroup = 1
            ),

            // --- Double spaces ---
            Rule(
                id = "DOUBLE_SPACE",
                pattern = Regex("\\S(  +)\\S"),
                message = { "Use a single space" },
                suggest = { listOf(" ") },
                matchGroup = 1
            )
        )
    }
}
