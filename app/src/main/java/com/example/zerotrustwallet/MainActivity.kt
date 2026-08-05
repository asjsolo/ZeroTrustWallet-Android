package com.example.zerotrustwallet

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// Brand Colors
val BrandBlue = Color(0xFF005DAA)
val BrandYellow = Color(0xFFFFD600)
val BrandLightBlue = Color(0xFF2E86C1)
val BrandBackground = Color(0xFFF5F5F5)

// Data Class for Memory
data class SavedPayee(val name: String, val account: String, val bank: String, val mobile: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val context = LocalContext.current

                // --- 1. INITIALIZE THE TEAM'S SENSORS ---
                val keystrokeExtractor = remember { KeystrokeFeatureExtractor() }
                val gestureExtractor = remember { GestureFeatureExtractor() }
                val imuExtractor = remember { IMUFeatureExtractor(context) }

                // Initialize the Master Brain
                val zkFusionEngine = remember { ZkFusionEngine(keystrokeExtractor, gestureExtractor, imuExtractor) }

                // Manage IMU lifecycle
                DisposableEffect(Unit) {
                    imuExtractor.startListening()
                    onDispose { imuExtractor.stopListening() }
                }

                // Shared App Memory
                var registeredPhone by remember { mutableStateOf("") }
                var registeredPin by remember { mutableStateOf("1234") }

                var savedPayees by remember { mutableStateOf(listOf<SavedPayee>()) }
                var selectedBankForPayee by remember { mutableStateOf("") }
                var selectedPayeeForTransfer by remember { mutableStateOf<SavedPayee?>(null) }

                // --- 2. CAPTURE GLOBAL GESTURES ---
                Box(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            val duration = change.uptimeMillis - change.previousUptimeMillis
                            gestureExtractor.recordGesture(dragAmount, duration)
                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
                        exitTransition = { fadeOut(animationSpec = tween(300)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
                    ) {
                        composable("login") { ZeroTrustLoginScreen(navController, registeredPin) }
                        composable("register") {
                            ZeroTrustRegisterScreen(navController) { newPhone, newPin ->
                                registeredPhone = newPhone; registeredPin = newPin
                            }
                        }
                        composable("dashboard") { ZeroTrustDashboardScreen(navController) }

                        // --- SEND MONEY FLOW (Wired to Keystroke) ---
                        composable("send_money") { SendMoneyScreen(navController, keystrokeExtractor) }
                        composable("send_money_detail/{mobile}") { backStackEntry ->
                            val mobile = backStackEntry.arguments?.getString("mobile") ?: ""
                            SendMoneyDetailScreen(navController, mobile, keystrokeExtractor)
                        }

                        // --- REQUEST MONEY FLOW (Wired to Keystroke) ---
                        composable("request_money") { RequestLandingScreen(navController) }
                        composable("request_money_form") { RequestMoneyFormScreen(navController, keystrokeExtractor) }

                        // --- MY QR FLOW ---
                        composable("my_qr") { MyQRLandingScreen(navController) }
                        composable("my_qr_generated") { MyQRGeneratedScreen(navController) }

                        // --- TRANSFER ROUTES (Wired to Keystroke) ---
                        composable("transfer_money") { TransferMoneyScreen(navController, selectedPayeeForTransfer, keystrokeExtractor) }
                        composable("select_payee") {
                            SelectPayeeScreen(navController, savedPayees) { payee ->
                                selectedPayeeForTransfer = payee
                                navController.popBackStack()
                            }
                        }
                        composable("add_payee") {
                            AddPayeeScreen(navController, selectedBankForPayee, keystrokeExtractor) { newPayee ->
                                savedPayees = savedPayees + newPayee
                                selectedBankForPayee = ""
                                navController.popBackStack()
                            }
                        }

                        // --- THE ZK FUSION EVALUATION SCREEN ---
                        composable("zk_biometric_transfer") { ZeroTrustBiometricScreen(navController, zkFusionEngine) }

                        // --- ADD MONEY FLOWS ---
                        composable("choose_add_option") { ChooseAddOptionScreen(navController) }
                        composable("add_card") { AddCardScreen(navController) }
                        composable("add_bank_wallet") { AddBankScreen(navController, "Add Bank Account") { navController.popBackStack() } }
                        composable("add_bank_payee") {
                            AddBankScreen(navController, "Select Bank") { bank ->
                                selectedBankForPayee = bank
                                navController.popBackStack()
                            }
                        }
                        composable("wallet") { WalletScreen(navController) }
                        composable("history") { TransactionHistoryScreen(navController) }
                        composable("notifications") { NotificationsScreen(navController) }
                    }
                }
            }
        }
    }
}

// --- CORE REUSABLE ZERO-TRUST TEXT FIELD (WIRED TO SAKITH'S ENGINE) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTrustTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    engine: KeystrokeFeatureExtractor,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = { newValue ->
            engine.recordTextChange(newValue) // Records flight times and backspaces!
            onValueChange(newValue)
        },
        label = { Text(label, color = Color.Gray) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = BrandBlue,
            unfocusedIndicatorColor = Color.LightGray,
            focusedTextColor = BrandBlue,
            unfocusedTextColor = Color.DarkGray
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// --- BIOMETRIC FUSION SCREEN (WIRED TO YOUR ENGINE) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTrustBiometricScreen(navController: NavController, fusionEngine: ZkFusionEngine) {
    var matrix by remember { mutableStateOf<TrustMatrix?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Run the evaluation when the screen loads
    LaunchedEffect(Unit) {
        matrix = fusionEngine.evaluateTrustMatrix()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZK-Trust Authentication", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text("Continuous Biometric Telemetry Matrix", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))

            if (matrix != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sakith: Keystroke Dynamics (${matrix!!.keystrokeScore.toInt()}%)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                        Slider(value = matrix!!.keystrokeScore, onValueChange = { }, valueRange = 0f..100f)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Kalani: Touch Gestures (${matrix!!.gestureScore.toInt()}%)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                        Slider(value = matrix!!.gestureScore, onValueChange = { }, valueRange = 0f..100f)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Oshani: IMU Sensor Path (${matrix!!.imuScore.toInt()}%)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue)
                        Slider(value = matrix!!.imuScore, onValueChange = { }, valueRange = 0f..100f)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
                ) { Text("AUTHORIZE TRANSACTION", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showDialog && matrix != null) {
        if (matrix!!.isAuthorized) {
            val proofHash = fusionEngine.generateCryptographicProof("TXN-9982")
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = { Button(onClick = { fusionEngine.wipeSecureSession(); showDialog = false; navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } }) { Text("OK") } },
                title = { Text("Transaction Authorized") },
                text = { Text("ZK-Trust score verified at ${matrix!!.finalScore.toInt()}%. Funds transferred successfully over serverless Postgres.\n\nProof: $proofHash") }
            )
        } else {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { Button(onClick = { fusionEngine.wipeSecureSession(); showDialog = false; navController.navigate("login") { popUpTo("login") { inclusive = true } } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Acknowledge Session Destruction") } },
                title = { Text("Zero-Trust Lockdown!", color = MaterialTheme.colorScheme.error) },
                text = { Text("Biometric anomaly detected (Trust Score: ${matrix!!.finalScore.toInt()}%). Backend SQL atomic transaction rolled back safely. Session terminated.") }
            )
        }
    }
}

// --- 1. LOGIN & REGISTER SCREENS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTrustLoginScreen(navController: NavController, validPin: String) {
    var pin by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(BrandBlue).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text("ZeroTrust", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = BrandYellow)
        Text("Secure Wallet System", fontSize = 14.sp, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("Welcome back,", fontSize = 22.sp, color = Color.White)
            Text("Test User", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(60.dp))
        TextField(
            value = pin,
            onValueChange = { pin = it },
            placeholder = { Text("Master PIN", color = Color.White.copy(alpha = 0.7f)) },
            visualTransformation = PasswordVisualTransformation(),
            trailingIcon = { Icon(Icons.Default.Visibility, contentDescription = "Show", tint = Color.White) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Color.White,
                cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Forgot PIN?", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = {
                if (pin == validPin) { navController.navigate("dashboard") } else { Toast.makeText(context, "Invalid Master PIN", Toast.LENGTH_SHORT).show() }
            },
            colors = ButtonDefaults.buttonColors(containerColor = BrandLightBlue),
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
        ) {
            Text("SECURE LOGIN", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.navigate("register") }) {
            Text("Don't have an account? Register", color = Color.White, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric Login", tint = Color.White, modifier = Modifier.size(60.dp))
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeroTrustRegisterScreen(navController: NavController, onRegisterSuccess: (String, String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(BrandBlue).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("ZeroTrust", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = BrandYellow)
        Text("Join the Secure Network", fontSize = 14.sp, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Register with your phone number", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
        }
        Spacer(modifier = Modifier.height(40.dp))
        TextField(
            value = phone, onValueChange = { phone = it }, placeholder = { Text("Phone Number", color = Color.White.copy(alpha = 0.7f)) },
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone", tint = Color.White) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Color.White, cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextField(
            value = pin, onValueChange = { pin = it }, placeholder = { Text("Create Master PIN", color = Color.White.copy(alpha = 0.7f)) }, visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.White) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Color.White, cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextField(
            value = confirmPin, onValueChange = { confirmPin = it }, placeholder = { Text("Confirm Master PIN", color = Color.White.copy(alpha = 0.7f)) }, visualTransformation = PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = "Confirm", tint = Color.White) },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.White, unfocusedIndicatorColor = Color.White, cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White), modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = {
                if (phone.isEmpty() || pin.isEmpty()) { Toast.makeText(context, "Fill out all fields", Toast.LENGTH_SHORT).show() }
                else if (pin != confirmPin) { Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show() }
                else {
                    onRegisterSuccess(phone, pin)
                    Toast.makeText(context, "Registration Successful!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = BrandLightBlue), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
        ) { Text("REGISTER ACCOUNT", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = { navController.popBackStack() }) { Text("Already have an account? Login", color = Color.White, fontSize = 14.sp) }
    }
}

// --- 2. SEND MONEY SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(navController: NavController, engine: KeystrokeFeatureExtractor) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var mobileNumber by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Send Money", fontSize = 16.sp, color = BrandBlue, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.White, contentColor = BrandBlue, indicator = { tabPositions -> TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = BrandBlue) }) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("Mobile Number") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("My Friends") })
            }
            if (selectedTabIndex == 0) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Enter a registered mobile number or select from contacts", fontSize = 14.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(24.dp))
                    ZeroTrustTextField(value = mobileNumber, onValueChange = { mobileNumber = it }, label = "Mobile Number", engine = engine, leadingIcon = { Text("+94 ", fontWeight = FontWeight.Bold, color = Color.Gray) }, trailingIcon = { Icon(Icons.Default.ContactPhone, null, tint = BrandBlue) })
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { if (mobileNumber.isNotEmpty()) navController.navigate("send_money_detail/$mobileNumber") },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandYellow), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
                    ) { Text("NEXT", color = BrandBlue, fontWeight = FontWeight.Bold) }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PeopleAlt, contentDescription = "Friends", tint = BrandBlue, modifier = Modifier.size(80.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Looks like you haven't\nsaved any friends yet.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Complete a transaction to a mobile number, and you can save the receiver's number as a \"Friend\" at the completion of the transfer.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyDetailScreen(navController: NavController, targetMobile: String, engine: KeystrokeFeatureExtractor) {
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Send Money", fontSize = 16.sp, color = BrandBlue) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color.White
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Pay From", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = BrandBlue), modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ZeroTrust Wallet - 185020062426", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("LKR 80,890.00", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Sending to: +94 $targetMobile", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
            Spacer(modifier = Modifier.height(32.dp))
            ZeroTrustTextField(value = amount, onValueChange = { amount = it }, label = "Enter amount", engine = engine, leadingIcon = { Text("LKR ", fontWeight = FontWeight.Bold, color = BrandBlue) })
            Spacer(modifier = Modifier.height(24.dp))
            ZeroTrustTextField(value = reference, onValueChange = { reference = it }, label = "Reference (Required)", engine = engine)
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = { navController.navigate("zk_biometric_transfer") },
                colors = ButtonDefaults.buttonColors(containerColor = if (amount.isNotEmpty()) BrandBlue else Color.LightGray), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("SEND", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- REQUEST MONEY FLOW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLandingScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Request Money", color = BrandBlue, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = BrandBackground
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.ReceiptLong, contentDescription = "Requests", tint = Color.LightGray, modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No Money Requests Sent", fontSize = 18.sp, color = BrandBlue, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("You haven't requested any money yet.", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = { navController.navigate("request_money_form") },
                colors = ButtonDefaults.buttonColors(containerColor = BrandYellow), modifier = Modifier.fillMaxWidth(0.8f).height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("CREATE MONEY REQUEST", color = BrandBlue, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestMoneyFormScreen(navController: NavController, engine: KeystrokeFeatureExtractor) {
    var mobile by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Request Money", color = BrandBlue, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Enter a mobile number or select from contacts", fontSize = 14.sp, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(24.dp))
            ZeroTrustTextField(value = mobile, onValueChange = { mobile = it }, label = "Enter mobile number", engine = engine, leadingIcon = { Text("+94 ", fontWeight = FontWeight.Bold, color = Color.Gray) }, trailingIcon = { Icon(Icons.Default.ContactPhone, null, tint = BrandBlue) })
            Spacer(modifier = Modifier.height(32.dp))
            ZeroTrustTextField(value = amount, onValueChange = { amount = it }, label = "Enter amount", engine = engine, leadingIcon = { Text("LKR ", fontWeight = FontWeight.Bold, color = BrandBlue) })
            Text("Min LKR 10.00, Max LKR 100,000.00", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.height(32.dp))
            ZeroTrustTextField(value = reference, onValueChange = { reference = it }, label = "Reference (Required)", engine = engine)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } },
                colors = ButtonDefaults.buttonColors(containerColor = if (mobile.isNotEmpty() && amount.isNotEmpty()) BrandYellow else Color.LightGray), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("NEXT", color = if (mobile.isNotEmpty() && amount.isNotEmpty()) BrandBlue else Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- MY QR FLOW ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRLandingScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("My QR", color = BrandBlue, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.Close, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(150.dp).background(BrandYellow.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scanner", tint = BrandBlue, modifier = Modifier.size(80.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Get Paid Instantly with\nMy QR", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BrandBlue, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Create a LankaQR for your ZeroTrust account and receive payments instantly from any LankaQR enabled app", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { navController.navigate("my_qr_generated") },
                colors = ButtonDefaults.buttonColors(containerColor = BrandYellow), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("Generate Static QR", color = BrandBlue, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQRGeneratedScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("My QR", color = BrandBlue, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.Close, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.QrCode2, contentDescription = "Generated QR Code", tint = Color.Black, modifier = Modifier.fillMaxSize(0.8f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("AHMED A J A", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandBlue, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(32.dp))
            Text("You can now start accepting\npayments using the QR code\nabove", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BrandBlue, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).background(BrandYellow, CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = BrandBlue)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Share", color = BrandBlue, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).background(BrandYellow, CircleShape).clickable { }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = BrandBlue)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Download", color = BrandBlue, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// --- 3. TRANSFER MONEY SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferMoneyScreen(navController: NavController, selectedPayee: SavedPayee?, engine: KeystrokeFeatureExtractor) {
    var amount by remember { mutableStateOf("") }
    var myRef by remember { mutableStateOf("") }
    var receiverRef by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Money", fontSize = 16.sp, color = BrandBlue, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Pay From", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = BrandBlue),
                modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ZeroTrust Wallet - 185020062426", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("LKR 80,890.00", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                onClick = { navController.navigate("select_payee") },
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F9FF)),
                border = BorderStroke(1.dp, BrandLightBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().height(70.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Transfer To", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
                        if (selectedPayee != null) {
                            Text("${selectedPayee.name} - ${selectedPayee.bank}", fontSize = 12.sp, color = Color.DarkGray)
                        } else {
                            Text("Select payee or own account", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Select", tint = BrandBlue)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            ZeroTrustTextField(
                value = amount, onValueChange = { amount = it }, label = "Enter amount", engine = engine, leadingIcon = { Text("LKR ", fontWeight = FontWeight.Bold, color = BrandBlue) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ZeroTrustTextField(
                value = myRef, onValueChange = { myRef = it }, label = "My Reference (Required)", engine = engine, trailingIcon = { Icon(Icons.Outlined.Info, contentDescription = "Info", tint = BrandLightBlue) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            ZeroTrustTextField(
                value = receiverRef, onValueChange = { receiverRef = it }, label = "Receiver Reference (Required)", engine = engine, trailingIcon = { Icon(Icons.Outlined.Info, contentDescription = "Info", tint = BrandLightBlue) }
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { navController.navigate("zk_biometric_transfer") },
                colors = ButtonDefaults.buttonColors(containerColor = if (amount.isNotEmpty() && selectedPayee != null) BrandBlue else Color.LightGray),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- 4. SELECT PAYEE SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPayeeScreen(navController: NavController, payees: List<SavedPayee>, onPayeeSelected: (SavedPayee) -> Unit) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Transfer To", fontSize = 16.sp, color = BrandBlue, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_payee") },
                containerColor = BrandYellow, contentColor = BrandBlue
            ) { Icon(Icons.Default.Add, contentDescription = "Add Payee") }
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTabIndex, containerColor = Color.White, contentColor = BrandBlue,
                indicator = { tabPositions -> TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = BrandBlue) }
            ) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("My Payees") })
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Own Accounts") })
            }

            if (payees.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.ListAlt, contentDescription = "Empty", tint = Color.LightGray, modifier = Modifier.size(100.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your saved payees will\nshow up here.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(payees) { payee ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { onPayeeSelected(payee) }.padding(16.dp)) {
                            Text(payee.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandBlue)
                            Text("${payee.bank} • ${payee.account}", fontSize = 14.sp, color = Color.Gray)
                            Divider(modifier = Modifier.padding(top = 16.dp), color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

// --- 5. ADD PAYEE SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPayeeScreen(
    navController: NavController,
    selectedBank: String,
    engine: KeystrokeFeatureExtractor,
    onSave: (SavedPayee) -> Unit
) {
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Payee", fontSize = 16.sp, color = BrandBlue, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp).verticalScroll(rememberScrollState())) {

            Text("Bank", fontSize = 12.sp, color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth().height(50.dp).clickable { navController.navigate("add_bank_payee") },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (selectedBank.isEmpty()) "Select bank" else selectedBank, color = if (selectedBank.isEmpty()) Color.LightGray else Color.Black, fontSize = 16.sp)
                Icon(Icons.Default.ChevronRight, contentDescription = "Select", tint = BrandLightBlue)
            }
            Divider(color = Color.LightGray)

            Spacer(modifier = Modifier.height(16.dp))

            ZeroTrustTextField(
                value = account,
                onValueChange = { account = it },
                label = "Enter account number",
                engine = engine
            )

            Spacer(modifier = Modifier.height(16.dp))

            ZeroTrustTextField(
                value = name,
                onValueChange = { name = it },
                label = "Enter account name",
                engine = engine
            )

            Spacer(modifier = Modifier.height(16.dp))

            ZeroTrustTextField(
                value = mobile,
                onValueChange = { mobile = it },
                label = "Enter mobile number (+94)",
                engine = engine
            )

            Spacer(modifier = Modifier.height(16.dp))

            ZeroTrustTextField(
                value = email,
                onValueChange = { email = it },
                label = "Enter email address",
                engine = engine
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedBank.isNotEmpty() && account.isNotEmpty() && name.isNotEmpty()) {
                        onSave(SavedPayee(name, account, selectedBank, mobile))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedBank.isNotEmpty() && account.isNotEmpty()) BrandBlue else Color.LightGray),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)
            ) { Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- 6. THE ADD BANK REUSABLE SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBankScreen(navController: NavController, title: String, onBankSelected: (String) -> Unit) {
    val banks = listOf(
        "Hatton National Bank PLC", "Bank of Ceylon", "CDB",
        "Commercial Bank PLC", "HDFC Bank", "LOLC Finance PLC",
        "NDB", "National Savings Bank", "Nations Trust Bank PLC",
        "Pan Asia Banking", "Peoples Bank", "People's Leasing"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontSize = 16.sp, color = BrandBlue) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            Text("Select your bank", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue, modifier = Modifier.padding(vertical = 16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()
            ) {
                items(banks) { bankName ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0E0E0)), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(110.dp).clickable { onBankSelected(bankName) }
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(bankName, fontSize = 10.sp, textAlign = TextAlign.Center, color = Color.DarkGray, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- 8. DASHBOARD AND SHARED NAVIGATION COMPONENTS ---

@Composable
fun ZeroTrustDashboardScreen(navController: NavController) {
    var showAddInstrumentDialog by remember { mutableStateOf(false) }

    Scaffold(bottomBar = { ZeroTrustBottomNav(navController) }) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(BrandBackground).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.fillMaxWidth().background(BrandBlue).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 30.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    Text("ZeroTrust", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = BrandYellow)
                    Row {
                        Icon(Icons.Outlined.VerifiedUser, contentDescription = "Safe", tint = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(Icons.Outlined.CardGiftcard, contentDescription = "Gift", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("LKR 80,890.00", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Hide", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text("Available Balance", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    OutlinedButton(onClick = { showAddInstrumentDialog = true }, border = BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("ADD MONEY") }
                }
                Spacer(modifier = Modifier.height(30.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MainActionButton(Icons.Default.Send, "Send") { navController.navigate("send_money") }
                    MainActionButton(Icons.Default.AccountBalance, "Transfer") { navController.navigate("transfer_money") }
                    MainActionButton(Icons.Default.CallReceived, "Request") { navController.navigate("request_money") }
                    MainActionButton(Icons.Default.QrCode, "My QR") { navController.navigate("my_qr") }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(16.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(BrandYellow, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("Secure Transactions Active", fontWeight = FontWeight.Bold, color = BrandBlue) }
                Spacer(modifier = Modifier.height(24.dp))
                GridMenu()
            }
        }
    }

    if (showAddInstrumentDialog) {
        Dialog(onDismissRequest = { showAddInstrumentDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = "Bank", tint = BrandBlue, modifier = Modifier.size(60.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(Icons.Default.CreditCard, contentDescription = "Card", tint = BrandLightBlue, modifier = Modifier.size(40.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Add an instrument to add money to your wallet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Add funds to your wallet by linking your bank account or card to ZeroTrust.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    TextButton(onClick = { showAddInstrumentDialog = false }) { Text("Not Now", color = Color.Gray, fontSize = 16.sp) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showAddInstrumentDialog = false; navController.navigate("choose_add_option") }, colors = ButtonDefaults.buttonColors(containerColor = BrandYellow), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)) { Text("ADD NOW", color = BrandBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(navController: NavController) {
    var cardNumber by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Add Debit/Credit Card", fontSize = 16.sp, color = BrandBlue) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandBlue) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) }, containerColor = Color.White
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(BrandBlue, RoundedCornerShape(16.dp)).padding(24.dp)) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("Card number", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.background(Color.Transparent, RoundedCornerShape(4.dp)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) { Text("**** **** **** ****", color = Color.White, letterSpacing = 2.sp) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("Lets start with your\ncard number", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
            Spacer(modifier = Modifier.height(40.dp))
            TextField(value = cardNumber, onValueChange = { cardNumber = it }, label = { Text("Card number", color = Color.Gray) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = BrandBlue, unfocusedIndicatorColor = Color.Gray, focusedTextColor = Color.Black, unfocusedTextColor = Color.Black), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = if (cardNumber.isNotEmpty()) BrandBlue else Color.LightGray), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(8.dp)) { Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun MainActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp), modifier = Modifier.size(60.dp)) { Icon(icon, contentDescription = label, tint = BrandBlue, modifier = Modifier.size(30.dp)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun GridMenu() {
    val items: List<Pair<String, ImageVector>> = listOf(
        Pair("Bill\nPayments", Icons.Default.Receipt), Pair("Mobile\nReloads", Icons.Default.PhoneAndroid), Pair("Direct\nPay", Icons.Default.Payment), Pair("Deals &\nCoupons", Icons.Default.LocalOffer),
        Pair("Loyalty &\nRewards", Icons.Default.CardMembership), Pair("Cash to\nMobile", Icons.Default.MobileFriendly), Pair("Split\nPay", Icons.Default.PieChart), Pair("Gifting", Icons.Default.CardGiftcard),
        Pair("Invite\nFriends", Icons.Default.PersonAdd), Pair("Spot\nFine", Icons.Default.Policy), Pair("Gov\nPay", Icons.Default.AccountBalanceWallet), Pair("App\nSettings", Icons.Default.Settings)
    )
    Column {
        for (i in items.indices step 4) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceAround) {
                for (j in 0 until 4) { if (i + j < items.size) { GridItem(items[i + j].first, items[i + j].second) } else { Spacer(modifier = Modifier.width(70.dp)) } }
            }
        }
    }
}

@Composable
fun GridItem(label: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
        Box(modifier = Modifier.size(50.dp).background(Color(0xFFF0F0F0), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = BrandBlue, modifier = Modifier.size(24.dp)) }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 10.sp, textAlign = TextAlign.Center, color = Color.DarkGray, lineHeight = 12.sp)
    }
}

@Composable
fun ZeroTrustBottomNav(navController: NavController) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 8.dp) {
        Box(modifier = Modifier.fillMaxWidth().height(65.dp)) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.navigate("dashboard") { popUpTo("dashboard") { inclusive = true } } }) { Icon(Icons.Default.Home, contentDescription = "Home", tint = BrandBlue) }
                // Wired Wallet Button
                IconButton(onClick = { navController.navigate("wallet") }) { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Wallet", tint = BrandBlue) }
                Spacer(modifier = Modifier.width(40.dp))
                // Wired History Button
                IconButton(onClick = { navController.navigate("history") }) { Icon(Icons.Default.History, contentDescription = "Transactions", tint = BrandBlue) }
                // Wired Notifications Button
                IconButton(onClick = { navController.navigate("notifications") }) { Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = BrandBlue) }
            }
            Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).size(65.dp).background(BrandYellow, CircleShape).padding(4.dp).clickable { navController.navigate("my_qr") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = BrandBlue, modifier = Modifier.size(35.dp))
            }
        }
    }
}

// --- FOOTER & WALLET SCREENS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet", fontSize = 16.sp, color = BrandBlue, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = BrandBlue) } },
                actions = { IconButton(onClick = { navController.navigate("choose_add_option") }) { Icon(Icons.Default.AddCircleOutline, contentDescription = "Add", tint = BrandBlue) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBackground)
            )
        },
        containerColor = BrandBackground
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("ZeroTrust Max", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandBlue, modifier = Modifier.padding(bottom = 12.dp))

            // Main Wallet Card
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BrandBlue)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.background(Color(0xFF81C784), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("Active", color = BrandBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Make Default", color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = true, onCheckedChange = {}, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandLightBlue))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ZeroTrust Max Account", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("1850111111", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Maruthamunai-185", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Available Balance", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            Text("LKR 80,890.00", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Add Money Button
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End)  {
                Button(
                    onClick = { navController.navigate("choose_add_option") },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandYellow),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADD MONEY", color = BrandBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Cards", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandBlue, modifier = Modifier.padding(bottom = 12.dp))

            // Saved Card Display
            Card(modifier = Modifier.fillMaxWidth().height(200.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BrandBlue)) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Default", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd))
                    Column(modifier = Modifier.align(Alignment.BottomStart)) {
                        Text("Card Number", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("4283 98** **** 8575", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nickname", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        Text("Asjath Hnb", color = Color.White, fontSize = 14.sp)
                    }
                    Text("VISA", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Notifications", color = BrandBlue, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, tint = BrandBlue, contentDescription = null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = BrandBackground
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = "Empty", tint = Color.LightGray, modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No New Notifications", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
            Spacer(modifier = Modifier.height(8.dp))
            Text("You're all caught up!", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseAddOptionScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZeroTrust", color = BrandYellow, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = BrandYellow) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandBlue)
            )
        },
        containerColor = BrandBlue
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Choose an option to begin", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(40.dp))

            Card(onClick = { navController.navigate("add_card") }, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, contentDescription = "Card", tint = Color.DarkGray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add a Debit/ Credit Card", fontSize = 16.sp, color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Card(onClick = { navController.navigate("add_bank_wallet") }, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().height(65.dp), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = "Bank", tint = Color.DarkGray)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Add a Bank Account", fontSize = 16.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History", color = BrandBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, tint = BrandBlue, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = BrandBackground
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            items(5) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transfer to John Doe", fontWeight = FontWeight.Bold, color = BrandBlue)
                        Text("- LKR 1,500.00", fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                    Text("Today, 10:45 AM", fontSize = 12.sp, color = Color.Gray)
                    Divider(modifier = Modifier.padding(top = 16.dp), color = Color.LightGray)
                }
            }
        }
    }
}