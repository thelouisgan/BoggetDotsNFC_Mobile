package com.ganlouis.nfc.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Card(
    var boggetID: String = "",
    var cardType: String = "",
    var cardholder: String = "",
    var tampProtected: Boolean = false,
    var edots: Int = 0
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "", false, 0)
}