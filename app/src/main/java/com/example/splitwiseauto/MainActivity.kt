package com.example.splitwiseauto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class Transaction(
    val bank: String,
    val type: String,
    val amount: String,
    val date: String,
    val merchant: String,
    val balance: String,
    val upi: String,
    val accountIdentifier: String? = null,
    val confidence: ParseConfidence = ParseConfidence.MEDIUM,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val receivedAtMillis: Long = System.currentTimeMillis(),
    // Foreign-currency spend ("PHP 518.09"): amount above is then INR —
    // exact when the bank gave it (or FxMemory found it), else an estimate.
    val originalAmount: Double? = null,
    val originalCurrency: String? = null,
    val fxEstimated: Boolean = false
)

enum class ParseConfidence { HIGH, MEDIUM, LOW }


class MainActivity : ComponentActivity() {

    companion object {
        /** Widget deep link: which bottom tab to show (pager page index). */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_HOME = 0
        const val TAB_FRIENDS = 1
    }

    /** Tab requested by the launching intent (widget); consumed by HomeScreen. */
    private var requestedTab by androidx.compose.runtime.mutableStateOf<Int?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequestedTab(intent)
    }

    private fun readRequestedTab(intent: android.content.Intent?) {
        intent?.getIntExtra(EXTRA_OPEN_TAB, -1)?.takeIf { it >= 0 }?.let { requestedTab = it }
        intent?.removeExtra(EXTRA_OPEN_TAB) // don't re-apply on rotation
    }

    /*
     * Request SMS + Notification permissions
     */
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val smsGranted =
                permissions[Manifest.permission.RECEIVE_SMS]
                    ?: (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECEIVE_SMS
                    ) == PackageManager.PERMISSION_GRANTED)

            val notificationGranted =
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    permissions[Manifest.permission.POST_NOTIFICATIONS]
                        ?: (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED)
                } else {
                    true
                }

            when {
                smsGranted && notificationGranted -> {
                    Toast.makeText(
                        this,
                        "Permissions granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                !smsGranted -> {
                    Toast.makeText(
                        this,
                        "SMS is off. Turn it on in Settings → App permissions",
                        Toast.LENGTH_LONG
                    ).show()
                }

                !notificationGranted -> {
                    Toast.makeText(
                        this,
                        "Notifications are off. Turn them on in Settings → App permissions",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            requestBackgroundLocationIfNeeded()
        }

    /*
     * "Allow all the time" location, requested separately from (and only after)
     * foreground location, since Android requires it as its own step and a
     * combined request silently drops it on newer versions. Without this, a bank
     * SMS detected while the app is in the background never gets a location —
     * only expenses added manually from inside the app (always foreground) do.
     */
    private val backgroundLocationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "SMS expenses won't get a place. Allow \"Location all the time\" in Settings → App permissions",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun requestBackgroundLocationIfNeeded() {
        val hasForegroundLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!hasForegroundLocation) {
            return
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return
        }

        val hasBackgroundLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasBackgroundLocation) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First launch: the intro (name, UPI ID) runs before anything else,
        // including the permission prompts.
        if (!IntroStore.isDone(this)) {
            startActivity(android.content.Intent(this, IntroActivity::class.java))
            finish()
            return
        }
        DisplayRefresh.requestHighest(this) // 90/120 Hz where the screen supports it
        CustomCategories.ensureLoaded(this)

        // One-time, silent: sort old "Other" expenses with the newer category rules.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            MerchantCategorizer.recheckOtherExpensesOnce(applicationContext)
        }

        requestRequiredPermissions()

        // Initialize the Google Maps SDK once, up front. Without this the
        // FIRST map shown in a fresh app process can throw
        // "IBitmapDescriptorFactory is not initialized" (confirmed on the
        // expense-detail map) — pre-initializing removes that timing race
        // for every map in the app, including the Map tab's marker pills.
        runCatching {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext)
        }

        // Scan & Pay: every 15 min, PENDING payments older than 30 min with no
        // matching bank SMS become UNCONFIRMED (idempotent — KEEP policy).
        // Also run one pass now, so a payment that went stale while the app
        // wasn't running is shown correctly straight away.
        PendingPaymentTimeoutWorker.schedule(this)
        // Trips: remind to close (or auto-close) trips that stopped being used.
        TripReminders.schedule(this)
        lifecycleScope.launch {
            PendingPaymentTimeoutWorker.markTimedOut(this@MainActivity)
        }

        readRequestedTab(intent)

        setContent {
            SplitleeTheme {
                HomeScreen(
                    requestedTab = requestedTab,
                    onRequestedTabShown = { requestedTab = null }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up bank SMS the receiver missed (battery saver / autostart
        // rules can block it while the app is in the background).
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { SmsIngestion.catchUpFromInbox(applicationContext) }
        }
        // Safety net: opening the app always brings the widget up to date.
        SplitleeWidget.requestUpdate(applicationContext)
    }


    private fun requestRequiredPermissions() {

        val permissionsToRequest = mutableListOf<String>()

        /*
         * SMS permission
         */
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(
                Manifest.permission.RECEIVE_SMS
            )
        }

        /*
         * Location permission
         *
         * Needed so a detected expense (manual or SMS-triggered) can be tagged
         * with where it happened.
         */
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        /*
         * Notification permission
         *
         * Required only on Android 13+
         */
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        if (permissionsToRequest.isNotEmpty()) {

            permissionLauncher.launch(
                permissionsToRequest.toTypedArray()
            )
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }
}


/*
 * Existing manual SMS testing screen
 *
 * We are keeping this for now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerScreen() {

    var smsText by remember {
        mutableStateOf("")
    }

    var transaction by remember {
        mutableStateOf<Transaction?>(null)
    }

    MaterialTheme {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            Text(
                text = "Expense Tracker",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            ModernTextField(
                value = smsText,
                onValueChange = { smsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = "Paste bank SMS",
                singleLine = false,
                height = 180.dp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Button(
                onClick = {
                    transaction = parseBankSms(smsText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Parse Transaction")
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            transaction?.let {

                Text(
                    text = "Transaction",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                TransactionCard(it)
            }
        }
    }
}


/*
 * Manual transaction result card
 */
@Composable
fun TransactionCard(
    transaction: Transaction
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = formatRupeeIndian(transaction.amount),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text("Bank: ${transaction.bank}")
            Text("Type: ${transaction.type}")
            Text("Date: ${transaction.date}")
            Text("Merchant: ${transaction.merchant}")
            Text("Balance: ${formatRupeeIndian(transaction.balance)}")
            Text("UPI: ${transaction.upi}")
        }
    }
}


/*
 * Generic Indian bank SMS parser.
 *
 * Handles debit/credit alerts across the major banks (SBI, HDFC, ICICI,
 * Axis, Kotak, PNB, Bank of Baroda, Canara, Union Bank of India, IDBI,
 * Yes Bank, IndusInd, IDFC FIRST, AU Small Finance, Standard Chartered,
 * Citibank, American Express) plus common UPI/card phrasing variants.
 * Anything that still doesn't match falls through to null so it's never
 * silently turned into a wrong expense.
 */
fun parseBankSms(
    sms: String,
    sender: String? = null,
    receivedAtMillis: Long = System.currentTimeMillis()
): Transaction? {
    // Only bank / card / UPI-app sender IDs can create expenses. A message
    // from a person's phone number ("money debited from ***1234 card ...")
    // must never be read as a real transaction. `sender == null` is the
    // manual "Paste bank SMS" path, where the user vouches for the text.
    if (sender != null && !isTransactionalSender(sender)) return null

    val message = sms.replace(Regex("\\s+"), " ").trim()
    if (message.isBlank()) return null

    // OTPs, promotions, and pure balance/limit notices must never create expenses.
    if (Regex(
            "\\b(otp|one[- ]time password|offer|cashback\\s+offer|statement generated|" +
                "login alert|will expire|is due|reminder|mini\\s+statement|available\\s+limit\\s+is)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(message)
    ) return null

    val debit = Regex(
        "\\b(debited|debit(?:ed)?\\s+(?:by|with|for)|spent|paid|purchase(?:d)?\\s+(?:of|for)?|" +
            "withdrawn|withdrawal\\s+of|payment\\s+of|txn\\s+of|charged(?:\\s+to)?|used\\s+(?:at|for)|" +
            "sent|trf(?:\\s+to)?|has\\s+been\\s+debited)\\b",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(message)
    val credit = Regex(
        "\\b(credited|credit(?:ed)?\\s+(?:with|to)|received|deposited|refund(?:ed)?(?:\\s+of)?|" +
            "has\\s+been\\s+credited)\\b",
        RegexOption.IGNORE_CASE
    ).containsMatchIn(message)
    if (!debit && !credit) return null

    // Amounts inside "Avl Bal / Avl Limit / Balance ..." are never the spend.
    fun firstSpendAmount(re: Regex): String? =
        re.findAll(message).firstOrNull { !isBalanceContext(message, it.range.first) }
            ?.groupValues?.getOrNull(1)
    // Foreign-currency spend ("PHP 518.09 spent using ... Card").
    val foreign = ForeignCurrency.find(message)
    val amountText =
        firstSpendAmount(Regex("""(?:rs\.?|inr|₹)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE))
            ?: firstSpendAmount(Regex("""\b([\d,]+(?:\.\d{1,2})?)\s*(?:rs\.?|inr|₹)\b""", RegexOption.IGNORE_CASE))
            // No currency marker at all, e.g. SBI UPI: "A/C X8888 debited by
            // 480.0 on date 28Sep26 trf to NAME Refno ...". Only a number
            // directly after "debited/credited by|with|for" counts.
            ?: Regex("""\b(?:debited|credited)\s+(?:by|with|for)\s+([\d,]+(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE)
                .find(message)?.groupValues?.getOrNull(1)
    val inrAmount = amountText?.replace(",", "")?.toDoubleOrNull()
    // A foreign spend with no INR figure: estimate (marked; FxMemory refines it).
    val fxEstimated = foreign != null && inrAmount == null
    val parsedAmount = inrAmount
        ?: foreign?.let { ForeignCurrency.estimateInr(it) }
        ?: return null
    if (parsedAmount <= 0.0) return null

    val accountIdentifier = extractAccountIdentifier(message)
    val bank = detectBank(message, sender)
    val reference = Regex(
        """\b(?:upi\s*(?:ref(?:erence)?)?\s*(?:no\.?)?\s*[:\-]?|rrn\s*[:\-]?|ref\s*(?:no)?\s*[:\-]?|txn\s*(?:id|no)?\s*[:\-]?)\s*([a-z0-9\-]{6,})""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1) ?: "Unknown"
    val balance = Regex(
        """\b(?:avl\.?\s*(?:bal(?:ance)?|lmt|limit)|available\s+(?:balance|limit)|bal(?:ance)?|clr\s+bal)\s*[:\-]?\s*(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1) ?: "Unknown"

    val date = Regex(
        """\b(\d{2}[-/]\d{2}[-/]\d{2,4}|\d{2}-[A-Za-z]{3}-\d{2,4}|\d{2}\s+[A-Za-z]{3}\s+\d{2,4})\b"""
    ).find(message)?.groupValues?.getOrNull(1)
        ?: SimpleDateFormat("dd-MMM-yy", Locale.US).format(Date(receivedAtMillis))

    val merchant = extractMerchant(message)
    val confidence = when {
        debit && accountIdentifier != null -> ParseConfidence.HIGH
        debit -> ParseConfidence.MEDIUM
        else -> ParseConfidence.LOW
    }

    return Transaction(
        bank = bank,
        type = if (debit) "DEBIT" else "CREDIT",
        amount = String.format(Locale.US, "%.2f", parsedAmount),
        date = date,
        merchant = merchant,
        balance = balance,
        upi = reference,
        accountIdentifier = accountIdentifier,
        confidence = confidence,
        receivedAtMillis = receivedAtMillis,
        originalAmount = foreign?.amount,
        originalCurrency = foreign?.currency,
        fxEstimated = fxEstimated
    )
}

/**
 * True for the sender IDs banks, card issuers and UPI/fintech apps (CRED,
 * slice, OneCard, Paytm…) send from; false for ordinary people.
 *
 * In India every business SMS goes out under a TRAI-DLT registered header:
 * alphanumeric, shown as `AX-HDFCBK`, `JM-CREDIN-S`, or just `HDFCBK` on some
 * phones. A person texting you always shows up as a phone number
 * (`+919876543210`, `09876543210`) — so any all-digit sender is rejected.
 * Promotional headers (the `-P` suffix) are rejected too: they're ads, not
 * transaction alerts.
 */
/**
 * True for text that can't be a merchant name: a link ("https://icici",
 * "icici.co/x", "www.x.com") or a bare UPI/transaction reference
 * ("UPI-66519120788"). Those SMS then show "Unknown" instead of junk.
 */
internal fun looksLikeLinkOrRef(text: String): Boolean {
    val t = text.trim().lowercase(Locale.ROOT)
    if (t.isEmpty()) return true
    if ("://" in t || t.startsWith("http") || t.startsWith("www.")) return true
    // A domain WITH a path ("icici.co/ICICIT/j/..") is a link; a bare
    // "Amazon.in" / "Flipkart.com" is a real merchant name and stays.
    if (Regex("""^[a-z0-9-]+\.(co|com|in|net|org|me|ly|io)/""").containsMatchIn(t)) return true
    if (Regex("""^upi[-/ ]?\d{6,}$""").matches(t)) return true
    return t.replace(Regex("[^0-9]"), "").length >= 8 && t.count { it.isLetter() } <= 3
}

fun isTransactionalSender(sender: String): Boolean {
    // Deliberately loose. The only thing that must never become an expense is
    // a message from a PERSON, and a person always shows up as a phone
    // number. Business senders are letter codes, but their exact shape
    // varies by bank, carrier and phone ("JD-IDBIBK-S", "IDBIBK",
    // "VM-IDBIBANK-T", "+JD-IDBIBK-S"...). The old strict pattern rejected
    // unusual-but-genuine bank codes and silently dropped real debits, so
    // now: any sender with letters is accepted, except promotional (-P).
    val raw = sender.trim().uppercase(Locale.ROOT)
    if (raw.isEmpty()) return false

    // Phone numbers: only digits once +, spaces, dashes and brackets are removed.
    val digitsOnly = raw.replace(Regex("[+\\s\\-()]"), "")
    if (digitsOnly.all { it.isDigit() }) return false

    // Needs at least two letters to count as a business sender code.
    if (raw.count { it in 'A'..'Z' } < 2) return false

    // Promotional header (TRAI "-P" suffix): ads, never transaction alerts.
    if (Regex("""-P$""").containsMatchIn(raw)) return false
    return true
}

/** Extracts only a masked suffix; full account/card numbers are never retained. */
fun extractAccountIdentifier(sms: String): String? {
    val labelledEnding = Regex(
        """(?:a/?c(?:count)?|acct|card)(?:\s+(?:no\.?|number))?\s+(?:ending(?:\s+(?:in|with))?\s+)?(?:no\.?\s*)?(?:x+|\*+)?\s*(\d{3,4})\b""",
        RegexOption.IGNORE_CASE
    ).find(sms)?.groupValues?.getOrNull(1)
    // "A/CX0508" / "A/C*1234": mask glued to A/C with no space (SBI UPI
    // alerts). Without this the number was lost and the debit landed on
    // the Slice (no account number) card.
    val attachedEnding = labelledEnding ?: Regex("""\b(?:a/c|acct)\s*[x*]+(\d{3,4})\b""", RegexOption.IGNORE_CASE)
        .find(sms)?.groupValues?.getOrNull(1)
    val maskedEnding = attachedEnding ?: Regex("""\b(?:x{2,}|\*{2,})(\d{3,4})\b""", RegexOption.IGNORE_CASE)
        .find(sms)?.groupValues?.getOrNull(1)
    return maskedEnding?.let { "****${it.padStart(4, '0')}" }
}

/** Known bank SMS sender codes, used when the message body doesn't spell out the bank name. */
private val KNOWN_BANK_SENDER_CODES = mapOf(
    "SBIINB" to "SBI", "SBIPSG" to "SBI", "SBI" to "SBI",
    "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank",
    "ICICIB" to "ICICI Bank", "ICICIT" to "ICICI Bank",
    "AXISBK" to "Axis Bank", "AXISB" to "Axis Bank",
    "KOTAKB" to "Kotak Bank", "KOTAK" to "Kotak Bank",
    "PNBSMS" to "PNB", "PUNBNK" to "PNB",
    "BOBTXN" to "Bank of Baroda", "BOBIBK" to "Bank of Baroda",
    "CANBNK" to "Canara Bank",
    "UNIONB" to "Union Bank of India", "UBOI" to "Union Bank of India",
    "IDBIBK" to "IDBI Bank", "IDBIBNK" to "IDBI Bank",
    "YESBNK" to "Yes Bank",
    "INDBNK" to "IndusInd Bank", "INDUSB" to "IndusInd Bank",
    "IDFCFB" to "IDFC FIRST Bank",
    "AUBANK" to "AU Small Finance Bank",
    "SCBANK" to "Standard Chartered",
    "CITIBK" to "Citibank",
    "AMEXIN" to "American Express",
    "RBLBNK" to "RBL Bank"
)

private fun detectBank(message: String, sender: String?): String {
    val inMessage = Regex(
        """\b(IDFC\s+FIRST\s+Bank|AU\s+Small\s+Finance\s+Bank|Standard\s+Chartered(?:\s+Bank)?|American\s+Express|Union\s+Bank\s+of\s+India|Bank\s+of\s+Baroda|Bank\s+of\s+India|Bank\s+of\s+Maharashtra|[A-Za-z]{2,}(?:\s+[A-Za-z]{2,})?\s+Bank)\b""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1)
    if (inMessage != null) {
        return inMessage.replaceFirst(
            Regex("""^(?:using|from|your|on|to|at|for|towards|via|with|by|the)\s+""", RegexOption.IGNORE_CASE),
            ""
        )
    }
    val normalizedSender = sender?.replace(Regex("[^A-Za-z0-9]"), "")?.uppercase(Locale.ROOT)
    val knownBank = normalizedSender?.let { code ->
        KNOWN_BANK_SENDER_CODES.entries.firstOrNull { code.contains(it.key) }?.value
    }
    return knownBank ?: normalizedSender?.takeIf { it.length >= 3 } ?: "Unknown Bank"
}

// Everyday brands people transact with constantly, checked FIRST — before any
// regex attempt — so a merchant name buried inside dispute/fraud-helpline
// boilerplate (e.g. "...dispute call 18001080/SMS BLOCK 1000 to
// 9215676766...Blinkit...") is still recognized correctly instead of being
// swallowed by that boilerplate text. Word-boundary matched, case-insensitive.
private val KNOWN_MERCHANT_KEYWORDS = listOf(
    "Swiggy", "Zomato", "Blinkit", "Zepto", "BigBasket", "Instamart",
    "Amazon", "Flipkart", "Myntra", "Ajio", "Meesho", "Nykaa",
    "Uber", "Ola", "Rapido", "IRCTC",
    "Netflix", "Hotstar", "Spotify",
    "Airtel", "Jio", "Vodafone", "BSNL",
    "Dominos", "Domino's", "McDonald's", "McDonalds", "KFC", "Starbucks", "Pizza Hut",
    "PVR", "INOX", "BookMyShow",
    "Decathlon", "DMart", "D-Mart", "Croma", "Reliance"
)

// Long names (5+ chars) only need to START a word: card SMS cut the
// merchant off, e.g. "FlipkartInterne" for "Flipkart Internet". Short ones
// (Ola, Jio, KFC…) must be whole words so "Ola" never matches "Cola".
private val KNOWN_MERCHANT_REGEXES = KNOWN_MERCHANT_KEYWORDS.map { keyword ->
    val tail = if (keyword.length >= 5) "" else """\b"""
    keyword to Regex("""\b${Regex.escape(keyword)}$tail""", RegexOption.IGNORE_CASE)
}

private fun knownMerchantKeyword(message: String): String? =
    KNOWN_MERCHANT_REGEXES.firstOrNull { (_, regex) -> regex.containsMatchIn(message) }?.first

private fun extractMerchant(message: String): String {
    // "NAME credited" — the receiver's name in the "payee credited" style
    // used by ICICI, IDBI, SBI and most UPI apps. Handles BOTH the form with
    // a balance before the name ("...; Bal Rs 9335.81 CP CORNER FOOD
    // credited") and, crucially, the far more common form WITHOUT one
    // ("...on 07-Sep-26; John Doe credited") — that second case used to fall
    // through to "Unknown". The optional balance prefix is stripped so only
    // the actual name is captured. Anchored so it can't reach across a
    // sentence/segment boundary (';' and 'Rs' aren't in the name class).
    val creditedMerchant = Regex(
        """(?:bal(?:ance)?\s*(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?\s+)?""" +
            """([a-z][a-z0-9 .&'_-]{1,40}?)\s+credited\b""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1)

    // "at MERCHANT" — stop at a sentence-ending period (not a decimal point),
    // a date ("on 07-09-26"), or a trailing keyword (upi/ref/txn/avl/bal/info)
    // so we never swallow the rest of the SMS. Takes the LAST occurrence,
    // since card/POS messages often say "...to your X Bank Card... at
    // MERCHANT on DATE" — an earlier "at" can belong to unrelated text.
    val stopLookahead =
        """(?=\s*(?:\.(?!\d)|;|$|\bon\s+\d|\bupi\b|\bref|\brrn\b|\butr\b|\btxn\b|\btrf\b|\bvia\b|\bavl\b|\bbal\b|\binfo\b|\bnot\s+you\b|\bnever\s+share\b|\bcredited\b))"""
    // Skip "at <link>" from marketing footers ("Know more about EMI
    // conversion at https://icici.co/...") — a URL is never the merchant.
    val atMerchant = Regex(
        """\bat\s+(.+?)$stopLookahead""",
        RegexOption.IGNORE_CASE
    ).findAll(message).toList()
        .mapNotNull { it.groupValues.getOrNull(1) }
        .lastOrNull { !looksLikeLinkOrRef(it) }

    // Card spends: "... spent using ICICI Bank Card XX1000 on 09-Oct-26 on
    // FlipkartInterne. Avl Limit..." — the merchant follows the DATE, after
    // a second "on" (some banks use "at"). Stops at the same boundaries.
    val dateThenMerchant = Regex(
        """\bon\s+\d{1,2}[-/ ](?:[a-z]{3}|\d{1,2})[-/ ]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?\s+(?:on|at)\s+(.+?)$stopLookahead""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1)

    // UPI reference embedding the merchant — two delimiter styles seen in the
    // wild: slash ("UPI/DR/123.../MERCHANT NAME/...", most banks) and hyphen
    // ("UPI-662052745980-Blinkit.", e.g. ICICI). Tried before the generic
    // "to X" fallback below because it's a structured, unambiguous field
    // rather than free text that might match unrelated boilerplate.
    val vpaMerchant = Regex("""\bvpa\s+([a-z0-9.\-_]+@[a-z0-9.\-]+)""", RegexOption.IGNORE_CASE)
        .find(message)?.groupValues?.getOrNull(1)
        ?: Regex("""\bupi/[a-z]{2,4}/\d+/([a-z0-9 .&'\-]{3,}?)/""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.getOrNull(1)
        ?: Regex("""\bupi-\d{4,}-([a-z0-9 .&'\-]{2,}?)(?=[.,]|\s|$)""", RegexOption.IGNORE_CASE)
            .find(message)?.groupValues?.getOrNull(1)

    // "to|towards MERCHANT" — last resort. Free text like this is the most
    // likely to accidentally match a bank's fraud/dispute-helpline boilerplate
    // ("To dispute call 18001080 ... to 9215676766") instead of the real
    // transaction detail, so anything that looks like that footer (mentions
    // "dispute"/"block"/"call", or is mostly a phone number) is rejected.
    val toMerchant = Regex(
        """\b(?:to|towards|via\s+upi\s+to)\s+(.+?)$stopLookahead""",
        RegexOption.IGNORE_CASE
    ).find(message)?.groupValues?.getOrNull(1)?.takeUnless {
        Regex("""\b(dispute|block|call|helpline|customer\s+care)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(it) ||
            it.replace(Regex("[^0-9]"), "").length >= 6
    }

    val candidate = listOf(
        knownMerchantKeyword(message), creditedMerchant, atMerchant, dateThenMerchant, vpaMerchant, toMerchant
    ).firstOrNull { it != null && !looksLikeLinkOrRef(it) }
    return candidate
        ?.trim()
        ?.trim('/', '-')
        ?.takeUnless {
            it.startsWith("upi", ignoreCase = true) ||
                it.matches(Regex("[0-9-]+")) ||
                it.isBlank()
        }
        ?: "Unknown"
}
