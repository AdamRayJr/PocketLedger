package com.adam.pocketledger

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { PocketLedgerApp() } }
}

private val Green=Color(0xFF0F766E)
private val money=NumberFormat.getCurrencyInstance()
private val dateFmt=SimpleDateFormat("MMM d, yyyy",Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PocketLedgerApp() {
    val context=LocalContext.current
    val db=remember { LedgerDb(context) }
    var tab by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(Unit) { db.processRecurringTransactions(); ReminderScheduler.rescheduleAll(context); revision++; if(android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    PocketLedgerTheme {
        Scaffold(topBar={ CenterAlignedTopAppBar(title={ Text("Pocket Ledger",fontWeight=FontWeight.Bold) }) },bottomBar={ NavigationBar { listOf("Home" to Icons.Default.Home,"Accounts" to Icons.Default.AccountBalance,"Budget" to Icons.Default.PieChart,"Bills" to Icons.Default.Notifications).forEachIndexed { i,p -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,null)},label={Text(p.first)}) } } }) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when(tab) { 0->Dashboard(db,revision);1->AccountsScreen(db,revision){revision++};2->BudgetScreen(db,revision){revision++};else->BillsScreen(db,revision){revision++} } } }
    }
}

@Composable private fun Dashboard(db:LedgerDb,revision:Int) {
    val accounts=remember(revision){db.accounts()};val tx=remember(revision){db.transactions()};val bills=remember(revision){db.bills()}
    val balance=accounts.sumOf{it.openingBalance}+tx.sumOf{it.amount};val now=System.currentTimeMillis();val upcoming=bills.filter{!it.paid}.take(4)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Total balance",style=MaterialTheme.typography.labelLarge);Text(money.format(balance),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Accounts",accounts.size.toString(),Modifier.weight(1f));Metric("Unpaid bills",bills.count{!it.paid}.toString(),Modifier.weight(1f))} }
        item { Text("Coming up",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        if(upcoming.isEmpty()) item { Text("No unpaid bills. Add one from the Bills tab.") }
        items(upcoming){ BillRow(it,now,null) }
        item { Text("Recent transactions",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        items(tx.take(5)){ ListItem(headlineContent={Text(it.title)},supportingContent={Text("${it.category} • ${dateFmt.format(Date(it.date))}")},trailingContent={Text(money.format(it.amount),color=if(it.amount>=0) Green else MaterialTheme.colorScheme.error)}) }
    }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier){Card(modifier){Column(Modifier.padding(16.dp)){Text(value,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(label)}}}

@Composable private fun AccountsScreen(db:LedgerDb,revision:Int,refresh:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var dialog by remember{mutableStateOf<String?>(null)};var importAccount by remember{mutableLongStateOf(0)};var importResult by remember{mutableStateOf<StatementResult?>(null)};var importError by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    val accounts=remember(revision){db.accounts()};val tx=remember(revision){db.transactions()};if(importAccount==0L&&accounts.isNotEmpty())importAccount=accounts.first().id
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){StatementParser.parse(PdfStatementReader.read(context,uri))}}.onSuccess{importResult=it}.onFailure{importError=it.message?:"The PDF could not be read"};busy=false}}}
    Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Accounts",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Row{IconButton(enabled=accounts.isNotEmpty()&&!busy,onClick={pdfPicker.launch(arrayOf("application/pdf"))}){Icon(Icons.Default.UploadFile,"Import PDF")};IconButton(onClick={dialog="transaction"}){Icon(Icons.Default.SwapVert,"Transaction")};FilledIconButton(onClick={dialog="account"}){Icon(Icons.Default.Add,"Account")}}};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());LazyColumn{items(accounts,key={it.id}){a->val bal=a.openingBalance+tx.filter{it.accountId==a.id}.sumOf{it.amount};ListItem(headlineContent={Text(a.name)},supportingContent={Text(a.type)},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){Text(money.format(bal),fontWeight=FontWeight.Bold);IconButton(onClick={db.deleteAccount(a.id);refresh()}){Icon(Icons.Default.Delete,"Delete account")}}})};item{if(tx.isNotEmpty())Text("Transactions",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=16.dp))};items(tx,key={it.id}){t->ListItem(headlineContent={Text(t.title)},supportingContent={Text("${t.category} • ${dateFmt.format(Date(t.date))}")},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){Text(money.format(t.amount));IconButton(onClick={db.deleteTransaction(t.id);refresh()}){Icon(Icons.Default.Delete,"Delete transaction")}}})}}}
    if(dialog=="account") AccountDialog({dialog=null}){n,t,b->db.addAccount(n,t,b);refresh()};if(dialog=="transaction") TransactionDialog(accounts,{dialog=null}){a,t,v,c,r->db.addTransaction(a,t,v,c,System.currentTimeMillis(),r);refresh()}
    importResult?.let{result->StatementImportDialog(result,accounts,importAccount,{importAccount=it},{importResult=null}){selected,candidates->val count=db.importTransactions(importAccount,selected);candidates.forEach{db.addBill(it.name,it.averageAmount,it.nextDueDate,1,3)};importResult=null;ReminderScheduler.rescheduleAll(context);refresh()}}
    importError?.let{AlertDialog(onDismissRequest={importError=null},title={Text("Import failed")},text={Text(it)},confirmButton={TextButton(onClick={importError=null}){Text("OK")}})}
}

@Composable private fun BudgetScreen(db:LedgerDb,revision:Int,refresh:()->Unit){var show by remember{mutableStateOf(false)};val budgets=remember(revision){db.budgets()};val tx=remember(revision){db.transactions()};val cal=Calendar.getInstance();cal.set(Calendar.DAY_OF_MONTH,1);cal.set(Calendar.HOUR_OF_DAY,0);val start=cal.timeInMillis;Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Monthly budget",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);FilledIconButton(onClick={show=true}){Icon(Icons.Default.Add,"Budget")}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(budgets,key={it.id}){b->val spent=-tx.filter{it.date>=start&&it.category==b.category&&it.amount<0}.sumOf{it.amount};Card{Column(Modifier.padding(16.dp).fillMaxWidth()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(b.category,fontWeight=FontWeight.Bold);Row(verticalAlignment=Alignment.CenterVertically){Text("${money.format(spent)} / ${money.format(b.monthlyLimit)}");IconButton(onClick={db.deleteBudget(b.id);refresh()}){Icon(Icons.Default.Delete,"Delete budget")}}};Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={if(b.monthlyLimit==0.0)0f else (spent/b.monthlyLimit).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}}}}};if(show) BudgetDialog({show=false}){c,l->db.addBudget(c,l);refresh()}}

@Composable private fun BillsScreen(db:LedgerDb,revision:Int,refresh:()->Unit){val context=LocalContext.current;var show by remember{mutableStateOf(false)};var history by remember{mutableStateOf(false)};val bills=remember(revision){db.bills()};val payments=remember(revision){db.payments()};Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(if(history)"Payment history" else "Bills",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Row{TextButton(onClick={history=!history}){Text(if(history)"Bills" else "History")};FilledIconButton(onClick={show=true}){Icon(Icons.Default.Add,"Bill")}}};LazyColumn{if(history)items(payments,key={it.id}){p->ListItem(headlineContent={Text(bills.find{it.id==p.billId}?.name?:"Bill")},supportingContent={Text(dateFmt.format(Date(p.paidAt)))},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){Text(money.format(p.amount));IconButton(onClick={db.deletePayment(p.id);refresh()}){Icon(Icons.Default.Delete,"Delete payment")}}})}else items(bills,key={it.id}){b->ListItem(headlineContent={Text(b.name,fontWeight=FontWeight.SemiBold)},supportingContent={Text(if(b.paid)"Paid" else "Due ${dateFmt.format(Date(b.dueDate))}")},trailingContent={Row(verticalAlignment=Alignment.CenterVertically){Column(horizontalAlignment=Alignment.End){Text(money.format(b.amount));if(!b.paid)TextButton(onClick={db.markPaid(b);ReminderScheduler.rescheduleAll(context);refresh()}){Text("Mark paid")}};IconButton(onClick={db.deleteBill(b.id);refresh()}){Icon(Icons.Default.Delete,"Delete bill")}}})}}};if(show) BillDialog({show=false}){n,a,d,r,m->val id=db.addBill(n,a,d,r,m);db.bills().find{it.id==id}?.let{ReminderScheduler.schedule(context,it)};refresh()}}

@Composable private fun BillRow(b:Bill,now:Long,onPaid:(()->Unit)?){val overdue=!b.paid&&b.dueDate<now;ListItem(headlineContent={Text(b.name,fontWeight=FontWeight.SemiBold)},supportingContent={Text(when{b.paid->"Paid";overdue->"Overdue • ${dateFmt.format(Date(b.dueDate))}";else->"Due ${dateFmt.format(Date(b.dueDate))}"},color=if(overdue)MaterialTheme.colorScheme.error else Color.Unspecified)},trailingContent={Column(horizontalAlignment=Alignment.End){Text(money.format(b.amount));if(!b.paid&&onPaid!=null)TextButton(onClick=onPaid){Text("Mark paid")}}})}

@Composable private fun AccountDialog(close:()->Unit,save:(String,String,Double)->Unit){var n by remember{mutableStateOf("")};var t by remember{mutableStateOf("Checking")};var b by remember{mutableStateOf("")};FormDialog("Add account",close,{save(n,t,b.toDoubleOrNull()?:0.0)},n.isNotBlank()){Field("Name",n){n=it};Field("Type",t){t=it};Field("Opening balance",b){b=it}}}
@Composable private fun TransactionDialog(accounts:List<Account>,close:()->Unit,save:(Long,String,Double,String,String)->Unit){var account by remember{mutableStateOf(accounts.firstOrNull()?.id?:0)};var title by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var cat by remember{mutableStateOf("General")};var recurring by remember{mutableStateOf("None")};FormDialog("Add transaction",close,{save(account,title,amount.toDoubleOrNull()?:0.0,cat,recurring)},account>0&&title.isNotBlank()&&amount.toDoubleOrNull()!=null){Text("Account: ${accounts.find{it.id==account}?.name?:"Create an account first"}");accounts.forEach{TextButton(onClick={account=it.id}){Text(it.name)}};Field("Description",title){title=it};Field("Amount (expense is negative)",amount){amount=it};Field("Category",cat){cat=it};Field("Repeat: None, Weekly, or Monthly",recurring){recurring=it}}}
@Composable private fun BudgetDialog(close:()->Unit,save:(String,Double)->Unit){var c by remember{mutableStateOf("")};var l by remember{mutableStateOf("")};FormDialog("Set category budget",close,{save(c,l.toDoubleOrNull()?:0.0)},c.isNotBlank()&&l.toDoubleOrNull()!=null){Field("Category",c){c=it};Field("Monthly limit",l){l=it}}}
@Composable private fun BillDialog(close:()->Unit,save:(String,Double,Long,Int,Int)->Unit){val context=LocalContext.current;var n by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var due by remember{mutableLongStateOf(System.currentTimeMillis()+86400000)};var repeat by remember{mutableStateOf("1")};var remind by remember{mutableStateOf("3")};FormDialog("Add bill",close,{save(n,a.toDoubleOrNull()?:0.0,due,repeat.toIntOrNull()?:1,remind.toIntOrNull()?:3)},n.isNotBlank()&&a.toDoubleOrNull()!=null){Field("Bill name",n){n=it};Field("Amount",a){a=it};OutlinedButton(onClick={val cal=Calendar.getInstance();DatePickerDialog(context,{_,y,m,d->cal.set(y,m,d,9,0,0);due=cal.timeInMillis},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show()}){Text("Due: ${dateFmt.format(Date(due))}")};Field("Repeat every X months (0 = once)",repeat){repeat=it};Field("Remind me X days before",remind){remind=it}}}

@Composable private fun StatementImportDialog(result:StatementResult,accounts:List<Account>,accountId:Long,onAccount:(Long)->Unit,close:()->Unit,save:(List<ParsedTransaction>,List<RecurringCandidate>)->Unit){
    val selected=remember(result){mutableStateListOf<ParsedTransaction>().apply{addAll(result.transactions)}}
    val recurring=remember(result){mutableStateListOf<RecurringCandidate>().apply{addAll(result.recurring)}}
    AlertDialog(onDismissRequest=close,title={Text("Review PDF import")},text={LazyColumn(Modifier.heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        item{Text("Import into",fontWeight=FontWeight.Bold)}
        items(accounts,key={"account-${it.id}"}){a->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=accountId==a.id,onClick={onAccount(a.id)});Text(a.name)}}
        item{HorizontalDivider();Text("${result.transactions.size} transactions found",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))}
        items(result.transactions,key={"tx-${it.date}-${it.description}-${it.amount}"}){t->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=t in selected,onCheckedChange={if(it)selected.add(t)else selected.remove(t)});Column(Modifier.weight(1f)){Text(t.description,maxLines=1);Text("${dateFmt.format(Date(t.date))} • ${t.category}",style=MaterialTheme.typography.bodySmall)};Text(money.format(t.amount))}}
        if(result.recurring.isNotEmpty()){item{HorizontalDivider();Text("Possible recurring bills",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp));Text("Selected items become monthly bills with a 3-day reminder.",style=MaterialTheme.typography.bodySmall)};items(result.recurring,key={"bill-${it.name}"}){r->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(checked=r in recurring,onCheckedChange={if(it)recurring.add(r)else recurring.remove(r)});Column(Modifier.weight(1f)){Text(r.name,maxLines=1);Text("Seen ${r.occurrences} times",style=MaterialTheme.typography.bodySmall)};Text(money.format(r.averageAmount))}}}
    }},confirmButton={Button(enabled=accountId>0&&selected.isNotEmpty(),onClick={save(selected.toList(),recurring.toList())}){Text("Import ${selected.size}")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})
}

@Composable private fun Field(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth())}
@Composable private fun FormDialog(title:String,close:()->Unit,save:()->Unit,enabled:Boolean,content:@Composable ColumnScope.()->Unit){AlertDialog(onDismissRequest=close,title={Text(title)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp),content=content)},confirmButton={Button(onClick=save,enabled=enabled){Text("Save")}},dismissButton={TextButton(onClick=close){Text("Cancel")}})}
