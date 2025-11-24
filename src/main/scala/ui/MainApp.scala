package ui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.Includes._

// === MOCK backend == Replace with imports from your backend code ===
case class Account(id: String, minorId: String, var balance: BigDecimal, limits: Limits)
case class Limits(monthly: Option[BigDecimal], perTransaction: Option[BigDecimal])
object Category extends Enumeration { val Food, Entertainment, Education, Travel = Value }
case class Transaction(
  id: String, fromAccountId: String, toPayeeId: Option[String],
  amount: BigDecimal, category: Category.Value, timestamp: java.time.Instant = java.time.Instant.now
)
class Repository {
  private var accounts = Map[String, Account]()
  private var transactions = List[Transaction]()
  def addAccount(acc: Account): Unit = accounts += acc.id -> acc
  def getAccount(id: String): Option[Account] = accounts.get(id)
  def addTransaction(tx: Transaction): Unit = transactions = tx :: transactions
  def getTransactions(accId: String): List[Transaction] = transactions.filter(_.fromAccountId == accId)
  def getMonthlySpending(accId: String): BigDecimal = getTransactions(accId).map(_.amount).sum
}
class ParentDashboardController(repo: Repository) {
  def processTransaction(accId: String, tx: Transaction): Either[String, Account] = {
    val acc = repo.getAccount(accId).get
    if (tx.amount > acc.limits.perTransaction.getOrElse(BigDecimal(99999))) Left("Exceeded Per Transaction Limit")
    else if (repo.getMonthlySpending(accId) + tx.amount > acc.limits.monthly.getOrElse(BigDecimal(999999))) Left("Exceeded Monthly Limit")
    else {
      acc.balance -= tx.amount
      repo.addTransaction(tx)
      Right(acc)
    }
  }
  def getPendingApprovals(parentId: String): List[Transaction] = List()
  def approveTransaction(parentId: String, txId: String): Boolean = true
}

// === END MOCK backend ===

object MainApp extends JFXApp3 {
  // Mock backend instance
  val repository = new Repository()
  val dashboardController = new ParentDashboardController(repository)
  val minorAccount = Account("acc123", "minor123", BigDecimal(10000), Limits(Some(5000), Some(1000)))
  repository.addAccount(minorAccount)

  override def start(): Unit = {
    val balanceLabel = new Label {
      text = s"Current Balance: ₹${minorAccount.balance}"
      style = "-fx-font-size: 18px; -fx-font-weight: bold;"
    }
    val monthlyLimit = minorAccount.limits.monthly.getOrElse(BigDecimal(0))
    val limitStatusLabel = new Label {
      val used = repository.getMonthlySpending(minorAccount.id)
      text = s"Monthly Limit: ₹$used / ₹$monthlyLimit"
    }
    val transactionTable = new TableView[Transaction](ObservableBuffer(repository.getTransactions(minorAccount.id))) {
      columns ++= List(
        new TableColumn[Transaction, String] {
          text = "Amount"
          cellValueFactory = {_.value.amount.toString}
          prefWidth = 80
        },
        new TableColumn[Transaction, String] {
          text = "Category"
          cellValueFactory = {_.value.category.toString}
          prefWidth = 100
        },
        new TableColumn[Transaction, String] {
          text = "Time"
          cellValueFactory = {_.value.timestamp.toString}
          prefWidth = 260
        }
      )
      prefHeight = 180
    }
    val makeTransactionButton = new Button("Simulate Food Transaction") {
      onAction = handle {
        val transaction = Transaction(java.util.UUID.randomUUID().toString, minorAccount.id, Some("payee1"), BigDecimal(500), Category.Food)
        dashboardController.processTransaction(minorAccount.id, transaction) match {
          case Right(acc) =>
            balanceLabel.text = s"Current Balance: ₹${acc.balance}"
            limitStatusLabel.text = s"Monthly Limit: ₹${repository.getMonthlySpending(minorAccount.id)} / ₹$monthlyLimit"
            transactionTable.items() += transaction
            showAlert("Success", "Transaction approved!")
          case Left(err) =>
            showAlert("Error", err)
        }
      }
    }
    val minorTab = new Tab {
      text = "Minor Dashboard"
      closable = false
      content = new VBox {
        padding = Insets(20)
        spacing = 15
        children = Seq(
          balanceLabel,
          limitStatusLabel,
          new Label("Recent Transactions:"),
          transactionTable,
          makeTransactionButton
        )
      }
    }

    val approvalBuffer = ObservableBuffer[Transaction]()
    val approvalsListView = new ListView[Transaction](approvalBuffer) {
      prefHeight = 100
    }
    val approveButton = new Button("Approve Selected") {
      onAction = handle {
        val selected = approvalsListView.selectionModel().getSelectedItem
        if (selected != null) {
          dashboardController.approveTransaction("parent123", selected.id)
          approvalBuffer.remove(selected)
          showAlert("Approved", s"Transaction ${selected.id} approved")
        }
      }
    }
    val parentTab = new Tab {
      text = "Parent Dashboard"
      closable = false
      content = new VBox {
        padding = Insets(20)
        spacing = 10
        children = Seq(
          new Label("Pending Approvals:"),
          approvalsListView,
          approveButton
        )
      }
    }

    val aiRiskTab = new Tab {
      text = "AI Risk"
      closable = false
      content = new VBox {
        padding = Insets(20)
        spacing = 15
        children = Seq(
          new Label("Demo: AI Risk Analysis coming soon! (stub)") {
            style = "-fx-font-size: 16px;"
          }
        )
      }
    }

    stage = new JFXApp3.PrimaryStage {
      title = "Youth Banking Demo"
      scene = new Scene(600, 450) {
        root = new TabPane {
          tabs = Seq(minorTab, parentTab, aiRiskTab)
        }
      }
    }
  }

  def showAlert(title: String, message: String): Unit = {
    new Alert(Alert.AlertType.Information) {
      headerText = title
      contentText = message
    }.showAndWait()
  }
}
