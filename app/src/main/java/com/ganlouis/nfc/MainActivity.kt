package com.ganlouis.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.text.HtmlCompat
import com.ganlouis.nfc.models.Card
import com.github.muellerma.nfc.record.ParsedNdefRecord
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database

class MainActivity : AppCompatActivity() {

    private var tagList: LinearLayout? = null
    private var nfcAdapter: NfcAdapter? = null

    private var cardContent: MaterialCardView? = null
    private var cardTypeText: TextView? = null
    private var idReversedHexText: TextView? = null
    private var tampProtectedText: TextView? = null
    private var boggetIdText: TextView? = null
    private var cardholderText: TextView? = null
    private var eDotsText: TextView? = null

    private lateinit var database: DatabaseReference

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = FirebaseDatabase.getInstance().reference

        cardContent = findViewById(R.id.card_view)
        cardTypeText = findViewById(R.id.card_type_text)
        idReversedHexText = findViewById(R.id.id_reversed_hex_text)
        tampProtectedText = findViewById(R.id.tamp_protected_text)
        boggetIdText = findViewById(R.id.bogget_id_text)
        cardholderText = findViewById(R.id.cardholder_text)
        eDotsText = findViewById(R.id.edots_text)

        tagList = findViewById<View>(R.id.list) as LinearLayout
        resolveIntent(intent)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            showNoNfcDialog()
            return
        }

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav?.menu?.findItem(R.id.nav_home)?.isChecked = true
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> {
                    // We're already on the home page
                    true
                }
                R.id.nav_boggetdots -> {
                    bottomNav?.menu?.findItem(R.id.nav_boggetdots)?.isChecked = true
                    val intent = Intent(this, BoggetDotsActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    true
                }
                else -> false
            }
        }

        // Set the home item as active when MainActivity is created
        updateActiveNavItem(R.id.nav_home)
    }

    private fun updateActiveNavItem(itemId: Int) {
        bottomNav.menu.findItem(itemId)?.isChecked = true
    }

    override fun onResume() {
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav?.menu?.findItem(R.id.nav_home)?.isChecked = true
        super.onResume()
        if (nfcAdapter?.isEnabled == false) {
            openNfcSettings()
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent_Mutable
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIntent(intent)
    }

    private fun showNoNfcDialog() {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.no_nfc)
            .setNeutralButton(R.string.close_app) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun openNfcSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_NFC)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        startActivity(intent)
    }

    private fun resolveIntent(intent: Intent) {
        val validActions = listOf(
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED
        )
        if (intent.action in validActions) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
            val idReversedHex = toReversedHex(tag.id)

            // Read NDEF Message if available
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            val messages = mutableListOf<NdefMessage>()

            if (rawMsgs != null) {
                for (rawMsg in rawMsgs) {
                    if (rawMsg is NdefMessage) {
                        messages.add(rawMsg)
                    }
                }
            }

            if (messages.isNotEmpty()) {
                // Process NDEF Message
                val records = messages[0].records
                if (records.size >= 5) {
                    val card = Card(
                        boggetID = String(records[0].payload, 3, records[0].payload.size - 3, Charsets.UTF_8),
                        cardType = String(records[1].payload, 3, records[1].payload.size - 3, Charsets.UTF_8),
                        cardholder = String(records[2].payload, 3, records[2].payload.size - 3, Charsets.UTF_8),
                        tampProtected = String(records[3].payload, 3, records[3].payload.size - 3, Charsets.UTF_8).toBoolean(),
                        edots = String(records[4].payload, 3, records[4].payload.size - 3, Charsets.UTF_8).toInt()
                    )

                    // Display card information
                    displayCardInfo(card, idReversedHex)

                    // TODO: Add code to match idReversedHex with Firebase database
                } else {
                    showToast("Insufficient records in NFC tag")
                }
            } else {
                // Handle non-NDEF formatted tag
                val tagData = dumpTagData(tag)
                displayRawTagData(tagData, idReversedHex)
            }

            // Build views for all records (if any)
            //buildTagViews(messages)
        }
    }


    private fun displayCardInfo(card: Card, idReversedHex: String) {
        cardTypeText?.text = card.cardType
        idReversedHexText?.text = idReversedHex
        tampProtectedText?.text = "TAMP Protected: ${card.tampProtected}"
        boggetIdText?.text = card.boggetID
        cardholderText?.text = card.cardholder
        eDotsText?.text = card.edots.toString()

        // Get firebase data
        database.child(idReversedHex).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val firebaseCard = snapshot.getValue(Card::class.java)
                if (firebaseCard != null) {
                    if (card != firebaseCard) {
                        showOverwriteDialog(card, firebaseCard, idReversedHex)
                    } else {
                        showToast("Card data matches database")
                    }
                }
            } else {
                promptToAddNewCard(card, idReversedHex)
            }
        }.addOnFailureListener { exception ->
            showToast("Failed to read data from database: ${exception.message}")
        }
    }


    private fun showOverwriteDialog(card: Card, firebaseCard: Card, idReversedHex: String) {
        val differences = mutableListOf<Pair<String, Pair<String, String>>>()
        if (card.boggetID != firebaseCard.boggetID) differences.add("Bogget ID" to (card.boggetID to firebaseCard.boggetID))
        if (card.cardType != firebaseCard.cardType) differences.add("Card Type" to (card.cardType to firebaseCard.cardType))
        if (card.cardholder != firebaseCard.cardholder) differences.add("Cardholder" to (card.cardholder to firebaseCard.cardholder))
        if (card.tampProtected != firebaseCard.tampProtected) differences.add("TAMP Protected" to (card.tampProtected.toString() to firebaseCard.tampProtected.toString()))
        if (card.edots != firebaseCard.edots) differences.add("eDots" to (card.edots.toString() to firebaseCard.edots.toString()))

        val dialogView = layoutInflater.inflate(R.layout.dialog_data_comparison, null)
        val dataComparisonTextView = dialogView.findViewById<TextView>(R.id.dataComparisonTextView)

        val comparisonText = buildString {
            differences.forEach { (field, values) ->
                appendLine("$field:")
                appendLine("  NFC Card: ${values.first}")
                appendLine("  Database: ${values.second}")
                appendLine()
            }
        }.trim()

        dataComparisonTextView.text = comparisonText

        MaterialAlertDialogBuilder(this)
            .setTitle("Sync Cards")
            .setView(dialogView)
            .setIcon(R.drawable.ic_boggetdots)
            .setMessage("Your BoggetDots Membership Card has some newer records. Do you want to update the database with your BoggetDots card?")
            .setPositiveButton("Update") { _, _ ->
                updateDatabaseWithCardData(card, idReversedHex)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateDatabaseWithCardData(card: Card, idReversedHex: String) {
        val databaseReference = FirebaseDatabase.getInstance().reference
        databaseReference.child(idReversedHex).setValue(card)
            .addOnSuccessListener {
                showToast("Sync successful")
            }
            .addOnFailureListener {
                showToast("Failed to update database")
            }
    }

    private fun promptToAddNewCard(card: Card, idReversedHex: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("New Card")
            .setMessage("Welcome to BoggetDots! Do you want to add this card to the database?")
            .setPositiveButton("Add") { _, _ ->
                updateDatabaseWithCardData(card, idReversedHex)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun displayRawTagData(tagData: String, idReversedHex: String) {
        val displayText = "ID (reversed hex): $idReversedHex\n\n$tagData"
        idReversedHexText?.text = displayText
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /*private fun dumpTagData(tag: Tag): String {
        val sb = StringBuilder()
        val id = tag.id
        return ""
    }*/

    private fun dumpTagData(tag: Tag): String {
        val sb = StringBuilder()
        val id = tag.id
        sb.append("ID (hex): ").append(toHex(id)).append('\n')
        sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n')
        sb.append("ID (dec): ").append(toDec(id)).append('\n')
        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n')
        val prefix = "android.nfc.tech."
        sb.append("Technologies: ")
        for (tech in tag.techList) {
            sb.append(tech.substring(prefix.length))
            sb.append(", ")
        }
        sb.delete(sb.length - 2, sb.length)
        for (tech in tag.techList) {
            if (tech == MifareClassic::class.java.name) {
                sb.append('\n')
                var type = "Unknown"
                try {
                    val mifareTag = MifareClassic.get(tag)

                    when (mifareTag.type) {
                        MifareClassic.TYPE_CLASSIC -> type = "Classic"
                        MifareClassic.TYPE_PLUS -> type = "Plus"
                        MifareClassic.TYPE_PRO -> type = "Pro"
                    }
                    sb.appendLine("Mifare Classic type: $type")
                    sb.appendLine("Mifare size: ${mifareTag.size} bytes")
                    sb.appendLine("Mifare sectors: ${mifareTag.sectorCount}")
                    sb.appendLine("Mifare blocks: ${mifareTag.blockCount}")
                } catch (e: Exception) {
                    sb.appendLine("Mifare classic error: ${e.message}")
                }
            }
            if (tech == MifareUltralight::class.java.name) {
                sb.append('\n')
                val mifareUlTag = MifareUltralight.get(tag)
                var type = "Unknown"
                when (mifareUlTag.type) {
                    MifareUltralight.TYPE_ULTRALIGHT -> type = "Ultralight"
                    MifareUltralight.TYPE_ULTRALIGHT_C -> type = "Ultralight C"
                }
                sb.append("Mifare Ultralight type: ")
                sb.append(type)
            }
        }
        return sb.toString()
    }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices.reversed()) {
            val b = bytes[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
            if (i > 0) {
                sb.append(" ")
            }
        }
        return sb.toString()
    }

    private fun toReversedHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices) {
            if (i > 0) {
                sb.append(":")
            }
            val b = bytes[i].toInt() and 0xff
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        return sb.toString()
    }

    private fun toDec(bytes: ByteArray): Long {
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices) {
            val value = bytes[i].toLong() and 0xffL
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun toReversedDec(bytes: ByteArray): Long {
        var result: Long = 0
        var factor: Long = 1
        for (i in bytes.indices.reversed()) {
            val value = bytes[i].toLong() and 0xffL
            result += value * factor
            factor *= 256L
        }
        return result
    }

    private fun buildTagViews(msgs: List<NdefMessage>) {
        if (msgs.isEmpty()) {
            return
        }
        val inflater = LayoutInflater.from(this)
        val content = tagList

        // Parse the first message in the list
        // Build views for all of the sub records
        val now = Date()
        val records = NdefMessageParser.parse(msgs[0])
        val size = records.size

        for (i in 0 until size) {
            val timeView = TextView(this)
            timeView.text = TIME_FORMAT.format(now)
            content!!.addView(timeView, 0)
            val record: ParsedNdefRecord = records[i]
            //cardTitle!!.text = String(msgs[0].records[0].payload)
            idReversedHexText?.text = String((msgs[0].records[0].payload), 3, (msgs[0].records[0].payload).size - 3, Charsets.UTF_8)
            //cardTitle!!.text = String((records[i] as NdefMessage).records[0].payload)
            content.addView(record.getView(this, inflater, content, i), 1 + i)
            content.addView(inflater.inflate(R.layout.tag_divider, content, false), 2 + i)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_main_clear -> {
                clearTags()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearTags() {
        for (i in tagList!!.childCount - 1 downTo 0) {
            val view = tagList!!.getChildAt(i)
            if (view.id != R.id.tag_viewer_text) {
                tagList!!.removeViewAt(i)
            }
        }
    }

    companion object {
        private val TIME_FORMAT = SimpleDateFormat.getDateTimeInstance()
    }
}