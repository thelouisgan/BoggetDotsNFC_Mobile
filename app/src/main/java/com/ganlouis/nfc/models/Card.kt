package com.ganlouis.nfc.models

data class Card(
    var BoggetID: String = "",
    var CardType: String? = "",
    var Cardholder: String? = "",
    var TAMPprotected: Boolean = false,
    var eDots: Int = 0
)