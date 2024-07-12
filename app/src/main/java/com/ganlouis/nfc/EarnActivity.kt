package com.ganlouis.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ganlouis.nfc.models.Card
import com.ganlouis.nfc.models.Product
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.database.FirebaseDatabase

class EarnActivity : AppCompatActivity() {

    private lateinit var productRecyclerView: RecyclerView
    private lateinit var earnFab: ExtendedFloatingActionButton
    private lateinit var productAdapter: ProductAdapter
    private var selectedProduct: Product? = null
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_earn)

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.menu.findItem(R.id.nav_earn)?.isChecked = true
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_boggetdots -> {
                    startActivity(Intent(this, BoggetDotsActivity::class.java))
                    true
                }
                R.id.nav_earn -> {
                    // Already in EarnActivity, no need to start a new activity
                    true
                }
                else -> false
            }
        }

        productRecyclerView = findViewById(R.id.productRecyclerView)
        earnFab = findViewById(R.id.earnFab)

        setupRecyclerView()
        setupEarnFab()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    override fun onResume() {
        bottomNav = findViewById(R.id.bottomNav)
        bottomNav?.menu?.findItem(R.id.nav_earn)?.isChecked = true
        super.onResume()
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(getProducts()) { product ->
            selectedProduct = product
            earnFab.show()
        }
        productRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productRecyclerView.adapter = productAdapter
    }

    private fun setupEarnFab() {
        earnFab.hide()
        earnFab.setOnClickListener {
            selectedProduct?.let { product ->
                showEarnDialog(product)
            }
        }
    }

    private fun showEarnDialog(product: Product) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Earn ${product.name}")
            .setMessage("Please tap your BoggetDots card to earn eDots.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                enableNfcForegroundDispatch()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun enableNfcForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun processNfcTag(tag: Tag, messages: List<NdefMessage>) {
        if (messages.isNotEmpty() && messages[0].records.size >= 5) {
            val records = messages[0].records
            val card = Card(
                boggetID = String(records[0].payload, 3, records[0].payload.size - 3, Charsets.UTF_8),
                cardType = String(records[1].payload, 3, records[1].payload.size - 3, Charsets.UTF_8),
                cardholder = String(records[2].payload, 3, records[2].payload.size - 3, Charsets.UTF_8),
                tampProtected = String(records[3].payload, 3, records[3].payload.size - 3, Charsets.UTF_8).toBoolean(),
                edots = String(records[4].payload, 3, records[4].payload.size - 3, Charsets.UTF_8).toInt()
            )

            selectedProduct?.let { product ->
                card.edots += product.price
                updateCardInDatabase(card)
                if (writeUpdatedCardToTag(tag, card, messages[0])) {
                    showToast("eDots earned successfully! New balance: ${card.edots}")
                } else {
                    showToast("Failed to update card. Please try again.")
                }
            }
        } else {
            showToast("Invalid card data")
        }
    }

    private fun writeUpdatedCardToTag(tag: Tag, card: Card, originalMessage: NdefMessage): Boolean {
        try {
            val ndef = Ndef.get(tag) ?: return false

            if (!ndef.isWritable) {
                showToast("Tag is not writable")
                return false
            }

            val updatedRecords = originalMessage.records.toMutableList()
            updatedRecords[4] = NdefRecord.createTextRecord(null, card.edots.toString())

            val updatedMessage = NdefMessage(updatedRecords.toTypedArray())

            ndef.connect()
            ndef.writeNdefMessage(updatedMessage)
            ndef.close()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error writing to tag: ${e.message}")
            return false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMessages ->
                val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
                tag?.let { processNfcTag(it, messages) }
            }
        }
    }

    private fun updateCardInDatabase(card: Card) {
        val database = FirebaseDatabase.getInstance().reference
        database.child(card.boggetID).setValue(card)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getProducts(): List<Product> {
        return listOf(
            Product("1 Minute Massage", 3, "https://ganlouis.com/wp-content/uploads/2023/08/M1.png"),
            Product("5 Minute Massage", 8, "https://ganlouis.com/wp-content/uploads/2023/08/M5.png"),
            Product("10 Minute Massage", 30, "https://ganlouis.com/wp-content/uploads/2023/08/M10.png"),
            Product("25 Minute Massage", 50, "https://ganlouis.com/wp-content/uploads/2023/08/M25.png"),
            Product("1 BoggetDot", 1, "https://ganlouis.com/wp-content/uploads/2023/08/B1.png"),
            Product("3 BoggetDots", 3, "https://ganlouis.com/wp-content/uploads/2023/08/B3.png"),
            Product("5 BoggetDots", 5, "https://ganlouis.com/wp-content/uploads/2023/08/B5.png"),
            Product("10 BoggetDots", 10, "https://ganlouis.com/wp-content/uploads/2023/08/B10.png"),
            Product("Find My Device", 6, "https://ganlouis.com/wp-content/uploads/2023/08/findmy.png"),
            Product("New Candace Book", 6, "https://ganlouis.com/wp-content/uploads/2023/08/cbph.png")
        )
    }
}