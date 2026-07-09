@file:Suppress("SameParameterValue")

package de.nicerdicer.db

import java.sql.Connection
import java.sql.DriverManager

object Database
{
    private const val DB_PATH = "db/nicerdicer.db"

    @Volatile
    private var initialized = false

    private fun connect(): Connection
    {
        try
        {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException)
        {
            println("Database: SQLite JDBC driver not found. Add dependency org.xerial:sqlite-jdbc. Error: ${e.message}")
            throw e
        }
        return DriverManager.getConnection("jdbc:sqlite:$DB_PATH")
    }

    fun init()
    {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            println("Database: Initializing SQLite DB at $DB_PATH ...")
            try
            {
                connect().use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS augments (
                                card TEXT PRIMARY KEY,
                                general TEXT,
                                blaster TEXT, breaker TEXT, brute TEXT, changer TEXT,
                                master TEXT, mover TEXT, shaker TEXT, stranger TEXT,
                                striker TEXT, tinker TEXT, thinker TEXT, trump TEXT
                            );
                            """.trimIndent()
                        )
                        stmt.execute("CREATE TABLE IF NOT EXISTS power_perks (card TEXT, name TEXT, text TEXT, meaning TEXT);")
                        stmt.execute("CREATE TABLE IF NOT EXISTS life_perks (card TEXT, name TEXT, text TEXT, meaning TEXT);")
                        stmt.execute("CREATE TABLE IF NOT EXISTS power_flaws (card TEXT, name TEXT, text TEXT, meaning TEXT);")
                        stmt.execute("CREATE TABLE IF NOT EXISTS life_flaws (card TEXT, name TEXT, text TEXT, meaning TEXT);")
                        stmt.execute("CREATE TABLE IF NOT EXISTS wounds (wound_type TEXT, wound_severity TEXT, wound_location TEXT, wound_name TEXT, wound_description TEXT);")
                        stmt.execute("CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY COLLATE NOCASE, owner TEXT, content TEXT);")

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS territories (
                                guild TEXT NOT NULL,
                                id INTEGER NOT NULL,
                                name TEXT COLLATE NOCASE,
                                owner_id TEXT,
                                owner_type TEXT,
                                color TEXT,
                                PRIMARY KEY (guild, id),
                                UNIQUE (guild, name)
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS alignment (
                                guild TEXT NOT NULL,
                                userId TEXT NOT NULL,
                                alignment_order TEXT NOT NULL,
                                intent TEXT NOT NULL,
                                PRIMARY KEY (guild, userId)
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS permissions (
                                guild TEXT PRIMARY KEY,
                                mod TEXT
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS reputation (
                                guild TEXT NOT NULL,
                                userId TEXT NOT NULL,
                                rep INTEGER NOT NULL,
                                PRIMARY KEY (guild, userId)
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS factions (
                                guild TEXT NOT NULL,
                                name TEXT NOT NULL COLLATE NOCASE,
                                ownerId TEXT NOT NULL,
                                roleId TEXT NOT NULL,
                                description TEXT,
                                image TEXT,
                                color TEXT NOT NULL,
                                memberList TEXT,
                                alignment TEXT NOT NULL,
                                PRIMARY KEY (guild, name),
                                UNIQUE (guild, roleId)
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS mook_classes (
                                name TEXT PRIMARY KEY COLLATE NOCASE,
                                pool TEXT NOT NULL,
                                description TEXT NOT NULL,
                                skill TEXT
                            );
                            """.trimIndent()
                        )

                        stmt.execute(
                            """
                            CREATE TABLE IF NOT EXISTS mook_backstories (
                                behavior TEXT NOT NULL,
                                history TEXT NOT NULL
                            );
                            """.trimIndent()
                        )
                    }

                    fillTableIfEmpty(conn, "augments", "/Perklist - Augments.csv") { _, row ->
                        conn.prepareStatement(
                            "INSERT OR IGNORE INTO augments(card,general,blaster,breaker,brute,changer,master,mover,shaker,stranger,striker,tinker,thinker,trump) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        ).use { ps ->
                            ps.setString(1, row["Card"] ?: "")
                            ps.setString(2, row["General"] ?: "")
                            ps.setString(3, row["Blaster"] ?: "")
                            ps.setString(4, row["Breaker"] ?: "")
                            ps.setString(5, row["Brute"] ?: "")
                            ps.setString(6, row["Changer"] ?: "")
                            ps.setString(7, row["Master"] ?: "")
                            ps.setString(8, row["Mover"] ?: "")
                            ps.setString(9, row["Shaker"] ?: "")
                            ps.setString(10, row["Stranger"] ?: "")
                            ps.setString(11, row["Striker"] ?: "")
                            ps.setString(12, row["Tinker"] ?: "")
                            ps.setString(13, row["Thinker"] ?: "")
                            ps.setString(14, row["Trump"] ?: "")
                            ps.executeUpdate()
                        }
                    }

                    fillSimpleFourColumnCsv(conn, "power_perks", "/Perklist - Power Perks.csv")
                    fillSimpleFourColumnCsv(conn, "life_perks", "/Perklist - Life Perks.csv")
                    fillSimpleFourColumnCsv(conn, "power_flaws", "/Perklist - Power Flaws.csv")
                    fillSimpleFourColumnCsv(conn, "life_flaws", "/Perklist - Life Flaws.csv")

                    // populate normalized wounds table if empty
                    val woundsRows = conn.createStatement().use { st ->
                        st.executeQuery("SELECT count(*) as c FROM wounds").use { rs -> if (rs.next()) rs.getInt("c") else 0 }
                    }
                    if (woundsRows == 0)
                    {
                        try
                        {
                            val parsed = parseWoundsFromJsonResource("/wounds.json")
                            if (parsed.isNotEmpty())
                            {
                                conn.autoCommit = false
                                try
                                {
                                    conn.prepareStatement("INSERT INTO wounds(wound_type,wound_severity,wound_location,wound_name,wound_description) VALUES(?,?,?,?,?)").use { ps ->
                                        for (w in parsed)
                                        {
                                            try
                                            {
                                                ps.setString(1, w.woundType)
                                                ps.setString(2, w.woundSeverity)
                                                ps.setString(3, w.woundLocation)
                                                ps.setString(4, w.woundName)
                                                ps.setString(5, w.woundDescription)
                                                ps.addBatch()
                                            } catch (ie: Exception)
                                            {
                                                println("Database: failed preparing insert for wound row: ${ie.message}")
                                                ie.printStackTrace()
                                            }
                                        }
                                        ps.executeBatch()
                                    }
                                    conn.commit()
                                    println("Database: inserted ${parsed.size} wounds into wounds table.")
                                } catch (e: Exception)
                                {
                                    conn.rollback()
                                    println("Database: rollback while inserting wounds due to: ${e.message}")
                                    e.printStackTrace()
                                } finally
                                {
                                    conn.autoCommit = true
                                }
                            } else
                            {
                                println("Database: no wounds parsed from JSON; wounds table left empty.")
                            }
                        } catch (e: Exception)
                        {
                            println("Database: failed to parse/insert wounds: ${e.message}")
                            e.printStackTrace()
                        }
                    } else
                    {
                        println("Database: wounds table already populated ($woundsRows rows).")
                    }

                    fillTableIfEmpty(conn, "mook_classes", "/mook_classes.csv") { _, row ->
                        conn.prepareStatement("INSERT INTO mook_classes(name,pool,description,skill) VALUES(?,?,?,?)").use { ps ->
                            ps.setString(1, row["Name"] ?: "")
                            ps.setString(2, row["Pool"] ?: "")
                            ps.setString(3, row["Description"] ?: "")
                            ps.setString(4, row["Skill"])
                            ps.executeUpdate()
                        }
                    }

                    fillTableIfEmpty(conn, "mook_backstories", "/mook_backstories.csv") { _, row ->
                        conn.prepareStatement("INSERT INTO mook_backstories(behavior,history) VALUES(?,?)").use { ps ->
                            ps.setString(1, row["Behavior"] ?: "")
                            ps.setString(2, row["Previous History (Why are they doing this)"] ?: "")
                            ps.executeUpdate()
                        }
                    }
                }
                initialized = true
                println("Database: initialization finished.")
            } catch (e: Exception)
            {
                println("Database: initialization failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    //region CSV Helpers

    private fun fillSimpleFourColumnCsv(conn: Connection, tableName: String, resourcePath: String)
    {
        fillTableIfEmpty(conn, tableName, resourcePath) { headers, row ->
            val nameKey = headers.find { it.equals("Perk Name", true) } ?: headers.find { it.equals("Flaw Name", true) } ?: "Perk Name"
            val textKey = headers.find { it.endsWith("Text", true) } ?: headers.find { it.contains("Text", true) } ?: "Perk Text"
            val meaningKey = headers.find { it.equals("CARD MEANING", true) } ?: "CARD MEANING"
            conn.prepareStatement("INSERT INTO $tableName(card,name,text,meaning) VALUES(?,?,?,?)").use { ps ->
                ps.setString(1, row["Card"] ?: "")
                ps.setString(2, row[nameKey] ?: "")
                ps.setString(3, row[textKey] ?: "")
                ps.setString(4, row[meaningKey] ?: "")
                ps.executeUpdate()
            }
        }
    }

    private fun fillTableIfEmpty(conn: Connection, tableName: String, resourcePath: String, inserter: (headers: List<String>, row: Map<String, String>) -> Unit)
    {
        try
        {
            val count = conn.createStatement().use { st ->
                st.executeQuery("SELECT count(*) as c FROM $tableName").use { rs -> if (rs.next()) rs.getInt("c") else 0 }
            }
            if (count > 0)
            {
                println("Database: table $tableName already populated ($count rows).")
                return
            }
            println("Database: populating $tableName from resource $resourcePath ...")
            val (headers, rows) = parseCsvFromResource(resourcePath)
            conn.autoCommit = false
            try
            {
                for (row in rows)
                {
                    try
                    {
                        inserter(headers, row)
                    } catch (ie: Exception)
                    {
                        println("Database: failed to insert row into $tableName: ${ie.message}")
                        ie.printStackTrace()
                    }
                }
                conn.commit()
                println("Database: populated $tableName with ${rows.size} rows.")
            } catch (e: Exception)
            {
                conn.rollback()
                println("Database: rollback populating $tableName due to ${e.message}")
                e.printStackTrace()
            } finally
            {
                conn.autoCommit = true
            }
        } catch (e: Exception)
        {
            println("Database: error checking/populating $tableName: ${e.message}")
            e.printStackTrace()
        }
    }

    // Multiline-aware CSV parser that respects quoted fields and escaped quotes ("")
    private fun parseCsvFromResource(resourcePath: String): Pair<List<String>, List<Map<String, String>>>
    {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Resource $resourcePath not found.")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val records = mutableListOf<List<String>>()
        val curField = StringBuilder()
        val curRecord = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length)
        {
            when (val c = text[i])
            {
                '"' ->
                {
                    // handle escaped double quote
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"')
                    {
                        curField.append('"')
                        i++
                    } else
                    {
                        inQuotes = !inQuotes
                    }
                }

                ',' ->
                {
                    if (inQuotes) curField.append(c) else
                    {
                        curRecord.add(curField.toString())
                        curField.setLength(0)
                    }
                }

                '\r' ->
                {
                    if (inQuotes)
                    {
                        curField.append(c)
                    } else
                    {
                        curRecord.add(curField.toString())
                        curField.setLength(0)
                        records.add(ArrayList(curRecord))
                        curRecord.clear()
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                    }
                }

                '\n' ->
                {
                    if (inQuotes) curField.append(c) else
                    {
                        curRecord.add(curField.toString())
                        curField.setLength(0)
                        records.add(ArrayList(curRecord))
                        curRecord.clear()
                    }
                }

                else -> curField.append(c)
            }
            i++
        }
        if (inQuotes)
        {
            println("Database.parseCsvFromResource: warning - file ended while still inside quoted field for $resourcePath")
        }
        if (curField.isNotEmpty() || curRecord.isNotEmpty())
        {
            curRecord.add(curField.toString())
            records.add(ArrayList(curRecord))
        }
        if (records.isEmpty()) return Pair(emptyList(), emptyList())
        val headers = records.first().map { it.trim().removeSurrounding("\"").trim() }
        val rows = mutableListOf<Map<String, String>>()
        for (rIdx in 1 until records.size)
        {
            val row = records[rIdx]
            val map = headers.mapIndexed { idx, h -> h to (if (idx < row.size) row[idx].trim().removeSurrounding("\"") else "") }.toMap()
            rows.add(map)
        }
        return Pair(headers, rows)
    }

    private fun readResourceAsText(resourcePath: String): String
    {
        val stream = javaClass.getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException("Resource $resourcePath not found.")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun parseWoundsFromJsonResource(resourcePath: String): List<WoundEntry>
    {
        val text = try
        {
            readResourceAsText(resourcePath)
        } catch (e: Exception)
        {
            println("Database.parseWoundsFromJsonResource: cannot read resource $resourcePath: ${e.message}")
            return emptyList()
        }
        val results = mutableListOf<WoundEntry>()

        val topKeyPattern = Regex("\"([^\"]+)\"\\s*:\\s*\\{")
        val matcher = topKeyPattern.findAll(text)
        for (m in matcher)
        {
            val typeKey = m.groupValues[1]
            val braceStart = text.indexOf('{', m.range.last)
            if (braceStart < 0) continue
            val (typeObj, _) = extractObjectBlock(text, braceStart) ?: continue

            val sevMatches = topKeyPattern.findAll(typeObj)
            for (sm in sevMatches)
            {
                val severityKey = sm.groupValues[1]
                val sevBraceStart = typeObj.indexOf('{', sm.range.last)
                if (sevBraceStart < 0) continue
                val (sevObj, _) = extractObjectBlock(typeObj, sevBraceStart) ?: continue

                val locMatches = topKeyPattern.findAll(sevObj)
                for (lm in locMatches)
                {
                    val locKey = lm.groupValues[1]
                    val locBraceStart = sevObj.indexOf('{', lm.range.last)
                    if (locBraceStart < 0) continue
                    val (locObj, _) = extractObjectBlock(sevObj, locBraceStart) ?: continue

                    val entryPattern = Regex("(?s)\"([^\"]+)\"\\s*:\\s*\"(.*?)\"")
                    for (em in entryPattern.findAll(locObj))
                    {
                        val woundName = em.groupValues[1]
                        val woundDesc = em.groupValues[2].replace("\"\"", "\"").trim()
                        results.add(WoundEntry(typeKey, severityKey, locKey, woundName, woundDesc))
                    }
                }
            }
        }
        println("Database.parseWoundsFromJsonResource: parsed ${results.size} wound entries from $resourcePath")
        return results
    }

    private fun extractObjectBlock(text: String, startIndex: Int): Pair<String, Int>?
    {
        var i = startIndex
        var depth = 0
        var inQuotes = false
        while (i < text.length)
        {
            val c = text[i]
            if (c == '"')
            {
                // handle escaped quotes
                if (inQuotes && i + 1 < text.length && text[i + 1] == '"')
                {
                    i += 2
                    continue
                }
                inQuotes = !inQuotes
                i++
                continue
            }
            if (!inQuotes)
            {
                if (c == '{') depth++
                else if (c == '}')
                {
                    depth--
                    if (depth == 0)
                    {
                        val obj = text.substring(startIndex, i + 1)
                        return Pair(obj, i)
                    }
                }
            }
            i++
        }
        return null
    }

    //endregion

    //region Data Getters

    fun getAugments(): List<AugmentEntry>
    {
        init()
        val list = mutableListOf<AugmentEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT * FROM augments").use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            val a = AugmentEntry(
                                card = rs.getString("card") ?: "",
                                general = rs.getString("general") ?: "",
                                blaster = rs.getString("blaster") ?: "",
                                breaker = rs.getString("breaker") ?: "",
                                brute = rs.getString("brute") ?: "",
                                changer = rs.getString("changer") ?: "",
                                master = rs.getString("master") ?: "",
                                mover = rs.getString("mover") ?: "",
                                shaker = rs.getString("shaker") ?: "",
                                stranger = rs.getString("stranger") ?: "",
                                striker = rs.getString("striker") ?: "",
                                tinker = rs.getString("tinker") ?: "",
                                thinker = rs.getString("thinker") ?: "",
                                trump = rs.getString("trump") ?: ""
                            )
                            list.add(a)
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getAugments failed: ${e.message}")
            e.printStackTrace()
        }
        return list
    }

    fun getPerks(tableName: String): List<PerkEntry>
    {
        init()
        val list = mutableListOf<PerkEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT card, name, text, meaning FROM $tableName").use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            val p = PerkEntry(
                                card = rs.getString("card") ?: "",
                                name = rs.getString("name") ?: "",
                                text = rs.getString("text") ?: "",
                                meaning = rs.getString("meaning") ?: ""
                            )
                            list.add(p)
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getPerks($tableName) failed: ${e.message}")
            e.printStackTrace()
        }
        return list
    }

    fun getWounds(): List<WoundEntry>
    {
        init()
        val list = mutableListOf<WoundEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT wound_type, wound_severity, wound_location, wound_name, wound_description FROM wounds").use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            list.add(
                                WoundEntry(
                                    woundType = rs.getString("wound_type") ?: "",
                                    woundSeverity = rs.getString("wound_severity") ?: "",
                                    woundLocation = rs.getString("wound_location") ?: "",
                                    woundName = rs.getString("wound_name") ?: "",
                                    woundDescription = rs.getString("wound_description") ?: ""
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getWounds failed: ${e.message}")
            e.printStackTrace()
        }
        return list
    }

    //endregion

    //region Tag CRUD and Helpers

    /**
     * Create a tag. Returns true on success, false on failure (already exists or error).
     */
    fun createTag(name: String, ownerId: String, content: String): Boolean
    {
        init()
        try
        {
            // check existence case-insensitively
            connect().use { conn ->
                conn.prepareStatement("SELECT name FROM tags WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            println("Database.createTag: tag '${name}' already exists (case-insensitive match).")
                            return false
                        }
                    }
                }
            }
            connect().use { conn ->
                conn.prepareStatement("INSERT INTO tags(name, owner, content) VALUES(?,?,?)").use { ps ->
                    ps.setString(1, name)
                    ps.setString(2, ownerId)
                    ps.setString(3, content)
                    ps.executeUpdate()
                }
            }
            println("Database.createTag: created tag '$name' by owner $ownerId")
            return true
        } catch (e: Exception)
        {
            // handle uniqueness / constraint messages gracefully
            val msg = e.message ?: "<no message>"
            if (msg.contains("constraint", true) || msg.contains("unique", true) || e is java.sql.SQLIntegrityConstraintViolationException)
            {
                println("Database.createTag: tag '$name' already exists (caught exception).")
                return false
            }
            println("Database.createTag failed for '$name': $msg")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Update a tag's content. Only the original owner may update.
     * Returns true on success, false otherwise.
     */
    fun updateTag(name: String, ownerId: String, newContent: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT owner FROM tags WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.updateTag: tag '$name' does not exist.")
                            return false
                        }
                        val existingOwner = rs.getString("owner") ?: ""
                        if (existingOwner != ownerId)
                        {
                            println("Database.updateTag: owner mismatch for '$name' (expected $existingOwner, got $ownerId).")
                            return false
                        }
                    }
                }
                conn.prepareStatement("UPDATE tags SET content = ? WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, newContent)
                    ps.setString(2, name)
                    val updated = ps.executeUpdate()
                    println("Database.updateTag: updated rows = $updated for tag '$name'")
                }
            }
            return true
        } catch (e: Exception)
        {
            println("Database.updateTag failed for '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Delete a tag. Only the original owner may delete.
     * Returns true on success, false otherwise.
     */
    fun deleteTag(name: String, ownerId: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT owner FROM tags WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.deleteTag: tag '$name' does not exist.")
                            return false
                        }
                        val existingOwner = rs.getString("owner") ?: ""
                        if (existingOwner != ownerId)
                        {
                            println("Database.deleteTag: owner mismatch for '$name' (expected $existingOwner, got $ownerId).")
                            return false
                        }
                    }
                }
                conn.prepareStatement("DELETE FROM tags WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    val deleted = ps.executeUpdate()
                    println("Database.deleteTag: deleted rows = $deleted for tag '$name'")
                }
            }
            return true
        } catch (e: Exception)
        {
            println("Database.deleteTag failed for '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Retrieve a single tag by name, or null if not found.
     */
    fun getTag(name: String): TagEntry?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT name, owner, content FROM tags WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return TagEntry(
                                name = rs.getString("name") ?: "",
                                owner = rs.getString("owner") ?: "",
                                content = rs.getString("content") ?: ""
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getTag failed for '$name': ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * List tags owned by a given user id.
     */
    fun listTagsByOwner(ownerId: String): List<TagEntry>
    {
        init()
        val res = mutableListOf<TagEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT name, owner, content FROM tags WHERE owner = ? ORDER BY name COLLATE NOCASE").use { ps ->
                    ps.setString(1, ownerId)
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            res.add(
                                TagEntry(
                                    name = rs.getString("name") ?: "",
                                    owner = rs.getString("owner") ?: "",
                                    content = rs.getString("content") ?: ""
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.listTagsByOwner failed for owner '$ownerId': ${e.message}")
            e.printStackTrace()
        }
        return res
    }

    //endregion

    //region Color Helpers
    /**
     * Map a territory type to its predefined color.
     */
    private fun getColorForType(type: String?): String
    {
        return when (type?.lowercase())
        {
            "good" -> "Yellow"
            "evil" -> "Purple"
            "quest" -> "Turquoise"
            "challenged" -> "DarkGray"
            else -> "White"  // default for unclaimed
        }
    }

    /**
     * Convert color name to hex for rendering (#RRGGBB).
     */
    private fun getHexForColor(color: String): String
    {
        return when (color)
        {
            "White" -> "#FFFFFF"
            "Yellow" -> "#FFFF00"
            "Purple" -> "#800080"
            "Turquoise" -> "#40E0D0"
            "DarkGray" -> "#A9A9A9"
            else -> "#FFFFFF"
        }
    }

    //endregion

    //region Territory Helpers

    /**
     * Claim a territory by numeric id within a guild. Only allowed if territory is unowned or already owned by caller.
     * Type can be "Good", "Evil", or "Quest"; otherwise defaults to unclaimed (White).
     * ownerType must be "user" or "faction".
     * Returns true on success.
     */
    fun claimTerritory(id: Int, ownerId: String, guildId: String, optionalName: String? = null, type: String? = null, ownerType: String = "user"): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT owner_id, owner_type FROM territories WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setInt(2, id)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            val existingOwnerId = rs.getString("owner_id")
                            if (existingOwnerId != null && existingOwnerId != ownerId)
                            {
                                println("Database.claimTerritory: territory $id in guild $guildId already owned by $existingOwnerId")
                                return false
                            }
                        }
                    }
                }
                val color = getColorForType(type)
                // ensure a row exists (create or update) for this guild
                if (optionalName != null)
                {
                    conn.prepareStatement(
                        "INSERT INTO territories(guild,id,name,owner_id,owner_type,color) VALUES(?,?,?,?,?,?) ON CONFLICT(guild,id) DO UPDATE SET owner_id=excluded.owner_id, owner_type=excluded.owner_type, name=COALESCE(excluded.name,territories.name), color=excluded.color"
                    ).use { ps ->
                        ps.setString(1, guildId)
                        ps.setInt(2, id)
                        ps.setString(3, optionalName)
                        ps.setString(4, ownerId)
                        ps.setString(5, ownerType)
                        ps.setString(6, color)
                        ps.executeUpdate()
                    }
                } else
                {
                    conn.prepareStatement(
                        "INSERT INTO territories(guild,id,owner_id,owner_type,color) VALUES(?,?,?,?,?) ON CONFLICT(guild,id) DO UPDATE SET owner_id=excluded.owner_id, owner_type=excluded.owner_type, color=excluded.color"
                    ).use { ps ->
                        ps.setString(1, guildId)
                        ps.setInt(2, id)
                        ps.setString(3, ownerId)
                        ps.setString(4, ownerType)
                        ps.setString(5, color)
                        ps.executeUpdate()
                    }
                }
            }
            println("Database.claimTerritory: owner $ownerId (type=$ownerType) claimed territory $id in guild $guildId with type $type")
            return true
        } catch (e: Exception)
        {
            println("Database.claimTerritory failed for id=$id owner=$ownerId guild=$guildId: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Release a territory in a specific guild. Only allowed if caller is owner or member of owning faction.
     * Resets the territory name to default ("Territory <id>") and color to "White" (unclaimed) when released.
     */
    fun releaseTerritory(id: Int, callerId: String, guildId: String, callerOwnerType: String = "user"): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT owner_id, owner_type FROM territories WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setInt(2, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.releaseTerritory: territory $id in guild $guildId does not exist.")
                            return false
                        }
                        val existingOwnerId = rs.getString("owner_id")
                        val existingOwnerType = rs.getString("owner_type")
                        if (existingOwnerId == null)
                        {
                            println("Database.releaseTerritory: territory $id in guild $guildId is already unowned.")
                            return false
                        }
                        
                        // Check ownership based on owner type
                        if (existingOwnerType == "faction")
                        {
                            // For faction territories, check if caller is a member
                            if (callerOwnerType != "faction" || callerId != existingOwnerId)
                            {
                                println("Database.releaseTerritory: caller $callerId is not a member of faction $existingOwnerId.")
                                return false
                            }
                        } else
                        {
                            // For user territories, check if caller is the owner
                            if (existingOwnerId != callerId)
                            {
                                println("Database.releaseTerritory: owner mismatch (expected $existingOwnerId, got $callerId) for guild $guildId.")
                                return false
                            }
                        }
                    }
                }
                // Reset owner, name to default, and color to White (unclaimed)
                val defaultName = "Territory $id"
                conn.prepareStatement("UPDATE territories SET owner_id = NULL, owner_type = NULL, name = ?, color = ? WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, defaultName)
                    ps.setString(2, "White")
                    ps.setString(3, guildId)
                    ps.setInt(4, id)
                    ps.executeUpdate()
                }
            }
            println("Database.releaseTerritory: caller $callerId released territory $id in guild $guildId and name reset to default.")
            return true
        } catch (e: Exception)
        {
            println("Database.releaseTerritory failed for id=$id caller=$callerId guild=$guildId: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Force-release a territory (used by moderators). Does not check ownership.
     */
    fun releaseTerritoryByModerator(id: Int, guildId: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                // Reset owner, name to default, and color to White (unclaimed)
                val defaultName = "Territory $id"
                conn.prepareStatement("UPDATE territories SET owner_id = NULL, owner_type = NULL, name = ?, color = ? WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, defaultName)
                    ps.setString(2, "White")
                    ps.setString(3, guildId)
                    ps.setInt(4, id)
                    val updated = ps.executeUpdate()
                    if (updated == 0)
                    {
                        println("Database.releaseTerritoryByModerator: territory $id in guild $guildId does not exist.")
                        return false
                    }
                }
            }
            println("Database.releaseTerritoryByModerator: territory $id in guild $guildId force-released by moderator.")
            return true
        } catch (e: Exception)
        {
            println("Database.releaseTerritoryByModerator failed for id=$id guild=$guildId: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Ensure every id in 'ids' exists in the territories table for the given guild with default values.
     * Idempotent: existing rows are left alone.
     */
    fun initializeTerritories(ids: Collection<Int>, guildId: String)
    {
        init()
        if (ids.isEmpty()) return
        try
        {
            connect().use { conn ->
                conn.autoCommit = false
                try
                {
                    conn.prepareStatement("INSERT OR IGNORE INTO territories(guild,id,name,owner_id,owner_type,color) VALUES(?,?,?,NULL,NULL,?)").use { ps ->
                        for (id in ids)
                        {
                            ps.setString(1, guildId)
                            ps.setInt(2, id)
                            ps.setString(3, "Territory $id")
                            ps.setString(4, "White")
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                    conn.commit()
                    println("Database.initializeTerritories: ensured ${ids.size} territory rows exist for guild $guildId.")
                } catch (e: Exception)
                {
                    conn.rollback()
                    println("Database.initializeTerritories: rollback due to ${e.message}")
                    e.printStackTrace()
                } finally
                {
                    conn.autoCommit = true
                }
            }
        } catch (e: Exception)
        {
            println("Database.initializeTerritories failed for guild $guildId: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Rename a territory in a guild. Only the original owner may rename.
     * For user-owned territories: caller must be the owner.
     * For faction-owned territories: caller must be a member of the faction.
     * Ensures name uniqueness (case-insensitive within guild). Returns true on success.
     */
    fun renameTerritory(id: Int, callerId: String, guildId: String, newName: String, callerOwnerType: String = "user"): Boolean
    {
        init()
        val trimmed = newName.trim()
        if (trimmed.isEmpty())
        {
            println("Database.renameTerritory: new name is empty.")
            return false
        }
        try
        {
            connect().use { conn ->
                // ensure territory exists and caller can rename it
                conn.prepareStatement("SELECT owner_id, owner_type FROM territories WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setInt(2, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.renameTerritory: territory $id in guild $guildId does not exist.")
                            return false
                        }
                        val existingOwnerId = rs.getString("owner_id")
                        val existingOwnerType = rs.getString("owner_type")
                        if (existingOwnerId == null)
                        {
                            println("Database.renameTerritory: territory $id in guild $guildId is unowned.")
                            return false
                        }
                        
                        // Check ownership based on owner type
                        if (existingOwnerType == "faction")
                        {
                            // For faction territories, check if caller is a member
                            if (callerOwnerType != "faction" || callerId != existingOwnerId)
                            {
                                println("Database.renameTerritory: caller $callerId is not a member of faction $existingOwnerId.")
                                return false
                            }
                        } else
                        {
                            // For user territories, check if caller is the owner
                            if (existingOwnerId != callerId)
                            {
                                println("Database.renameTerritory: owner mismatch (expected $existingOwnerId, got $callerId) for territory $id in guild $guildId.")
                                return false
                            }
                        }
                    }
                }
                // ensure new name not used by another territory in the same guild (case-insensitive)
                conn.prepareStatement("SELECT id FROM territories WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, trimmed)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            val foundId = rs.getInt("id")
                            if (foundId != id)
                            {
                                println("Database.renameTerritory: name '$trimmed' already used by territory $foundId in guild $guildId.")
                                return false
                            }
                        }
                    }
                }
                // perform update
                conn.prepareStatement("UPDATE territories SET name = ? WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, trimmed)
                    ps.setString(2, guildId)
                    ps.setInt(3, id)
                    val updated = ps.executeUpdate()
                    println("Database.renameTerritory: updated rows = $updated for territory $id in guild $guildId -> name='$trimmed'")
                }
            }
            return true
        } catch (e: Exception)
        {
            println("Database.renameTerritory failed for id=$id caller=$callerId guild=$guildId newName='$newName': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Set a territory to "Challenged" color. Can be called by anyone.
     * Returns true on success.
     */
    fun challengeTerritory(id: Int, guildId: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT id FROM territories WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setInt(2, id)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.challengeTerritory: territory $id in guild $guildId does not exist.")
                            return false
                        }
                    }
                }
                conn.prepareStatement("UPDATE territories SET color = ? WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, "DarkGray")
                    ps.setString(2, guildId)
                    ps.setInt(3, id)
                    ps.executeUpdate()
                }
            }
            println("Database.challengeTerritory: territory $id in guild $guildId marked as challenged.")
            return true
        } catch (e: Exception)
        {
            println("Database.challengeTerritory failed for id=$id guild=$guildId: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Update a territory's color.
     * Returns true on success.
     */
    fun updateTerritoryColor(id: Int, guildId: String, newColor: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("UPDATE territories SET color = ? WHERE guild = ? AND id = ?").use { ps ->
                    ps.setString(1, newColor)
                    ps.setString(2, guildId)
                    ps.setInt(3, id)
                    ps.executeUpdate()
                }
            }
            println("Database.updateTerritoryColor: territory $id in guild $guildId color updated to $newColor.")
            return true
        } catch (e: Exception)
        {
            println("Database.updateTerritoryColor failed for id=$id guild=$guildId newColor=$newColor: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * List all territories for a given guild with color and owner type.
     */
    fun listTerritories(guildId: String): List<TerritoryEntry>
    {
        init()
        val res = mutableListOf<TerritoryEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    """
                    SELECT t.id as id, t.name as name, t.owner_id as owner_id, t.owner_type as owner_type, t.color as color
                    FROM territories t
                    WHERE t.guild = ?
                    ORDER BY t.id
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            res.add(
                                TerritoryEntry(
                                    id = rs.getInt("id"),
                                    name = rs.getString("name") ?: "Territory ${rs.getInt("id")}",
                                    ownerId = rs.getString("owner_id"),
                                    ownerType = rs.getString("owner_type"),
                                    color = rs.getString("color") ?: "White"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.listTerritories failed for guild $guildId: ${e.message}")
            e.printStackTrace()
        }
        return res
    }

    //endregion

    //region Alignment Helpers

    /**
     * Set or update a user's alignment for a guild. Returns true on success.
     */
    fun setAlignment(guildId: String, userId: String, ord: String, intent: String): Boolean
    {
        init()
        val validOrders = listOf("Lawful", "Neutral", "Chaotic")
        val validIntents = listOf("Good", "Neutral", "Evil")

        if (ord !in validOrders || intent !in validIntents)
        {
            println("Database.setAlignment: invalid order '$ord' or intent '$intent'")
            return false
        }

        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    "INSERT INTO alignment(guild, userId, alignment_order, intent) VALUES(?, ?, ?, ?) ON CONFLICT(guild, userId) DO UPDATE SET alignment_order=excluded.alignment_order, intent=excluded.intent"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, userId)
                    ps.setString(3, ord)
                    ps.setString(4, intent)
                    ps.executeUpdate()
                }
            }
            println("Database.setAlignment: set alignment for user $userId in guild $guildId to $ord $intent")
            return true
        } catch (e: Exception)
        {
            println("Database.setAlignment failed for user '$userId' guild='$guildId': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Get a user's alignment for a guild. Returns AlignmentEntry or null if not found.
     */
    fun getAlignment(guildId: String, userId: String): AlignmentEntry?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT guild, userId, alignment_order, intent FROM alignment WHERE guild = ? AND userId = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, userId)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return AlignmentEntry(
                                guildId = rs.getString("guild"),
                                userId = rs.getString("userId"),
                                alignmentOrder = rs.getString("alignment_order"),
                                intent = rs.getString("intent")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getAlignment failed for user '$userId' guild='$guildId': ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /** Lists all saved alignments in a guild for chart counts and alignment-based player views. */
    fun getAlignments(guildId: String): List<AlignmentEntry>
    {
        init()
        val alignments = mutableListOf<AlignmentEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT guild, userId, alignment_order, intent FROM alignment WHERE guild = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            alignments.add(
                                AlignmentEntry(
                                    guildId = rs.getString("guild"),
                                    userId = rs.getString("userId"),
                                    alignmentOrder = rs.getString("alignment_order"),
                                    intent = rs.getString("intent"),
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getAlignments failed for guild '$guildId': ${e.message}")
            e.printStackTrace()
        }
        return alignments
    }

    //endregion

    //region Permission Helpers

    fun setModRole(guildId: String, roleId: String): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    "INSERT INTO permissions(guild, mod) VALUES(?, ?) ON CONFLICT(guild) DO UPDATE SET mod=excluded.mod"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, roleId)
                    ps.executeUpdate()
                }
            }
            println("Database.setModRole: set mod role $roleId for guild $guildId")
            return true
        } catch (e: Exception)
        {
            println("Database.setModRole failed for guild '$guildId' role='$roleId': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun getModRole(guildId: String): String?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT mod FROM permissions WHERE guild = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) return rs.getString("mod")
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getModRole failed for guild '$guildId': ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    //endregion

    //region Reputation Helpers

    fun addToReputation(guildId: String, userId: String, amount: Int): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    "INSERT INTO reputation(guild, userId, rep) VALUES(?, ?, ?) ON CONFLICT(guild, userId) DO UPDATE SET rep = reputation.rep + excluded.rep"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, userId)
                    ps.setInt(3, amount)
                    ps.executeUpdate()
                }
            }
            println("Database.addToReputation: added $amount to reputation for user $userId in guild $guildId")
            return true
        } catch (e: Exception)
        {
            println("Database.addToReputation failed for user '$userId' guild='$guildId': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun getReputation(guildId: String, userId: String): ReputationEntry?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT guild, userId, rep FROM reputation WHERE guild = ? AND userId = ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, userId)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return ReputationEntry(
                                guildId = rs.getString("guild"),
                                userId = rs.getString("userId"),
                                amount = rs.getInt("rep")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getReputation failed for user '$userId' guild='$guildId': ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    //endregion

    //region Faction Helpers

    /**
     * Create a faction. Returns true on success, false on failure (already exists or error).
     */
    fun createFaction(
        guildId: String,
        name: String,
        ownerId: String,
        roleId: String,
        description: String,
        color: String,
        alignment: String
    ): Boolean
    {
        init()
        try
        {
            // check existence case-insensitively
            connect().use { conn ->
                conn.prepareStatement("SELECT name FROM factions WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            println("Database.createFaction: faction '$name' already exists in guild $guildId (case-insensitive match).")
                            return false
                        }
                    }
                }
            }
            connect().use { conn ->
                conn.prepareStatement(
                    "INSERT INTO factions(guild, name, ownerId, roleId, description, image, color, memberList, alignment) VALUES(?,?,?,?,?,?,?,?,?)"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    ps.setString(3, ownerId)
                    ps.setString(4, roleId)
                    ps.setString(5, description)
                    ps.setString(6, null)
                    ps.setString(7, color)
                    ps.setString(8, ownerId)  // initialize memberList with owner
                    ps.setString(9, alignment)
                    ps.executeUpdate()
                }
            }
            println("Database.createFaction: created faction '$name' in guild $guildId by owner $ownerId with role $roleId")
            return true
        } catch (e: Exception)
        {
            val msg = e.message ?: "<no message>"
            if (msg.contains("constraint", true) || msg.contains("unique", true) || e is java.sql.SQLIntegrityConstraintViolationException)
            {
                println("Database.createFaction: faction '$name' already exists in guild $guildId (caught exception).")
                return false
            }
            println("Database.createFaction failed for '$name': $msg")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Update a faction. All updates are done in a single transaction.
     * Returns true on success, false otherwise.
     */
    fun updateFaction(
        guildId: String,
        name: String,
        ownerId: String,
        newDescription: String?,
        newImage: String?,
        newColor: String,
        newMemberList: String,
        alignment: String
    ): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                // First verify faction exists
                conn.prepareStatement("SELECT ownerId FROM factions WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.updateFaction: faction '$name' does not exist in guild $guildId.")
                            return false
                        }
                    }
                }

                // Start transaction
                val autoCommitBefore = conn.autoCommit
                conn.autoCommit = false
                try
                {
                    // Update all fields
                    var updated = 0

                    conn.prepareStatement("UPDATE factions SET description = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, newDescription)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    conn.prepareStatement("UPDATE factions SET image = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, newImage)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    conn.prepareStatement("UPDATE factions SET color = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, newColor)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    conn.prepareStatement("UPDATE factions SET ownerId = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, ownerId)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    conn.prepareStatement("UPDATE factions SET memberList = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, newMemberList)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    conn.prepareStatement("UPDATE factions SET alignment = ? WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                        ps.setString(1, alignment)
                        ps.setString(2, guildId)
                        ps.setString(3, name)
                        updated += ps.executeUpdate()
                    }

                    println("Database.updateFaction: executed ${updated / 6} faction updates for '$name' in guild $guildId")
                    conn.commit()
                    println("Database.updateFaction: successfully updated faction '$name' in guild $guildId")
                    return true
                } catch (e: Exception)
                {
                    conn.rollback()
                    println("Database.updateFaction: transaction rolled back for '$name': ${e.message}")
                    e.printStackTrace()
                    return false
                } finally
                {
                    conn.autoCommit = autoCommitBefore
                }
            }
        } catch (e: Exception)
        {
            println("Database.updateFaction failed for '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Delete a faction. Only the original owner or a moderator may delete.
     * Returns true on success, false otherwise.
     */
    fun deleteFaction(
        guildId: String,
        name: String
    ): Boolean
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT ownerId FROM factions WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    ps.executeQuery().use { rs ->
                        if (!rs.next())
                        {
                            println("Database.deleteFaction: faction '$name' does not exist in guild $guildId.")
                            return false
                        }
                    }
                }
                conn.prepareStatement("DELETE FROM factions WHERE guild = ? AND name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    val deleted = ps.executeUpdate()
                    println("Database.deleteFaction: deleted $deleted faction row(s) for '$name' in guild $guildId")
                }
            }
            return true
        } catch (e: Exception)
        {
            println("Database.deleteFaction failed for '$name': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Retrieve a single faction by name, or null if not found.
     */
    fun getFaction(guildId: String, name: String): FactionEntry?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    "SELECT guild, name, ownerId, roleId, description, image, color, memberList, alignment FROM factions WHERE guild = ? AND name = ? COLLATE NOCASE"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return FactionEntry(
                                guildId = rs.getString("guild"),
                                name = rs.getString("name"),
                                factionOwnerId = rs.getString("ownerId"),
                                factionRoleId = rs.getString("roleId"),
                                description = rs.getString("description") ?: "",
                                image = rs.getString("image"),
                                color = rs.getString("color"),
                                memberList = rs.getString("memberList") ?: "",
                                alignment = rs.getString("alignment") ?: ""
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getFaction failed for '$name' in guild $guildId: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    /**
     * List all factions in a guild.
     */
    fun listFactions(guildId: String): List<FactionEntry>
    {
        init()
        val res = mutableListOf<FactionEntry>()
        try
        {
            connect().use { conn ->
                conn.prepareStatement(
                    "SELECT guild, name, ownerId, roleId, description, image, color, memberList, alignment FROM factions WHERE guild = ? ORDER BY name COLLATE NOCASE"
                ).use { ps ->
                    ps.setString(1, guildId)
                    ps.executeQuery().use { rs ->
                        while (rs.next())
                        {
                            res.add(
                                FactionEntry(
                                    guildId = rs.getString("guild"),
                                    name = rs.getString("name"),
                                    factionOwnerId = rs.getString("ownerId"),
                                    factionRoleId = rs.getString("roleId"),
                                    description = rs.getString("description") ?: "",
                                    image = rs.getString("image"),
                                    color = rs.getString("color"),
                                    memberList = rs.getString("memberList") ?: "",
                                    alignment = rs.getString("alignment") ?: ""
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.listFactions failed for guild $guildId: ${e.message}")
            e.printStackTrace()
        }
        return res
    }

    fun getFactionOfUser(guildId: String, userId: String): FactionEntry?
    {
        init()
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT guild, name, ownerId, roleId, description, image, color, memberList, alignment FROM factions WHERE guild = ? AND memberList LIKE ?").use { ps ->
                    ps.setString(1, guildId)
                    ps.setString(2, "%$userId%")
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return FactionEntry(
                                guildId = rs.getString("guild"),
                                name = rs.getString("name"),
                                factionOwnerId = rs.getString("ownerId"),
                                factionRoleId = rs.getString("roleId"),
                                description = rs.getString("description") ?: "",
                                image = rs.getString("image"),
                                color = rs.getString("color"),
                                memberList = rs.getString("memberList") ?: "",
                                alignment = rs.getString("alignment") ?: ""
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getFactionOfUser failed for user '$userId' in guild $guildId: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    //endregion

    //region Mook Helpers

    fun getMookClassByName(name: String): MookClassEntry?
    {
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT name, pool, description, skill FROM mook_classes WHERE name = ? COLLATE NOCASE").use { ps ->
                    ps.setString(1, name)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return MookClassEntry(
                                name = rs.getString("name"),
                                pool = rs.getString("pool"),
                                description = rs.getString("description"),
                                skill = rs.getString("skill")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getMookClassByName failed for '$name': ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    fun getRandomMookClass(targetPool: String): MookClassEntry?
    {
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT name, pool, description, skill FROM mook_classes WHERE pool = ? COLLATE NOCASE ORDER BY RANDOM() LIMIT 1").use { ps ->
                    ps.setString(1, targetPool)
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return MookClassEntry(
                                name = rs.getString("name"),
                                pool = rs.getString("pool"),
                                description = rs.getString("description"),
                                skill = rs.getString("skill")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getRandomMookClass failed: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    fun getRandomMookBackstory(): MookBackstoryEntry?
    {
        try
        {
            connect().use { conn ->
                conn.prepareStatement("SELECT behavior, history FROM mook_backstories ORDER BY RANDOM() LIMIT 1").use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next())
                        {
                            return MookBackstoryEntry(
                                behavior = rs.getString("behavior"),
                                history = rs.getString("history")
                            )
                        }
                    }
                }
            }
        } catch (e: Exception)
        {
            println("Database.getRandomMookBackstory failed: ${e.message}")
            e.printStackTrace()
        }
        return null
    }

    //endregion
}
