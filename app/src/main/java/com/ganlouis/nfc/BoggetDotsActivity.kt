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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.database.FirebaseDatabase

class BoggetDotsActivity : AppCompatActivity() {

    private lateinit var productRecyclerView: RecyclerView
    private lateinit var purchaseFab: ExtendedFloatingActionButton
    private lateinit var productAdapter: ProductAdapter
    private var selectedProduct: Product? = null
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_boggetdots)

        productRecyclerView = findViewById(R.id.productRecyclerView)
        purchaseFab = findViewById(R.id.purchaseFab)

        setupRecyclerView()
        setupPurchaseFab()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(getProducts()) { product ->
            selectedProduct = product
            purchaseFab.show()
        }
        productRecyclerView.layoutManager = GridLayoutManager(this, 2)
        productRecyclerView.adapter = productAdapter
    }

    private fun setupPurchaseFab() {
        purchaseFab.hide()
        purchaseFab.setOnClickListener {
            selectedProduct?.let { product ->
                showPurchaseDialog(product)
            }
        }
    }

    private fun showPurchaseDialog(product: Product) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Purchase ${product.name}")
            .setMessage("Please tap your BoggetDots card to complete the purchase.")
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
                if (card.edots >= product.price) {
                    card.edots -= product.price
                    updateCardInDatabase(card)
                    if (writeUpdatedCardToTag(tag, card, messages[0])) {
                        showToast("Purchase successful! Remaining eDots: ${card.edots}")
                    } else {
                        showToast("Purchase failed. Please try again.")
                    }
                } else {
                    showToast("Insufficient eDots. Current balance: ${card.edots}")
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

    private fun updateNfcTag(message: NdefMessage, card: Card) {
        val records = message.records
        records[4] = NdefRecord.createTextRecord(null, card.edots.toString())
        val updatedMessage = NdefMessage(records)
        // Write the updated message to the NFC tag
        // Note: This part requires additional implementation to write to the NFC tag
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getProducts(): List<Product> {
        return listOf(
            Product("5 minutes", 4, "https://ganlouis.com/wp-content/uploads/2023/08/NEW5PHONE.png"),
            Product("10 minutes", 7, "https://ganlouis.com/wp-content/uploads/2023/08/NEW10PHONE.png"),
            Product("30 minutes", 18, "https://ganlouis.com/wp-content/uploads/2023/08/NEW30PHONE.png"),
            Product("1 hour", 33, "https://ganlouis.com/wp-content/uploads/2023/08/NEW1HPHONE.png"),
            Product("Minecraft 10 minutes", 12, "https://ganlouis.com/wp-content/uploads/2023/08/MC10.png"),
            Product("Minecraft 30 minutes", 30, "https://ganlouis.com/wp-content/uploads/2023/08/MC30.png"),
            Product("Minecraft 1 hour", 50, "https://ganlouis.com/wp-content/uploads/2023/08/MC1H.png"),
            Product("Massage Economy", 10, "https://ganlouis.com/wp-content/uploads/2023/08/ECONOMY-1.png"),
            Product("Massage Premium", 19, "https://ganlouis.com/wp-content/uploads/2023/08/PREMIUM-1.png"),
            Product("Massage Platinum King Deluxe Tent", 50, "https://ganlouis.com/wp-content/uploads/2023/08/KINGTENT.png"),
            Product("Bogget Bus", 10, "https://ganlouis.com/wp-content/uploads/2023/08/BB.png"),
            Product("Bogget Bus Yummy", 13, "https://ganlouis.com/wp-content/uploads/2023/08/BBB.png"),
            Product("Fun Pass", 3, "https://ganlouis.com/wp-content/uploads/2023/08/FP.png")
        )
    }
}