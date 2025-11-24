package ui

import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.Includes._
import java.time.Instant
import java.util.UUID

// Import your actual domain classes
import domain._
import services._
import controllers._

object MainApp extends JFXApp3 {
  
  // Initialize your actual backend services
  val repository = new Repository()
  val aiRiskEngine = new AIRiskEngine()
  val fraudDetection = new AdvancedFraudDetectionService(repository)
  val monitoringService = new MonitoringService(repository, aiRiskEngine)
  val transactionController = new TransactionController(repository, monitoringService)
  
  // Create demo account with proper structure
  val demoAccount = Account(
    id = "acc_demo_123",
    minorId = "minor_demo_456",
    balance = BigDecimal(10000),
    lockedFunds = Map.empty,
    limits = Limits(
      monthly = Some(BigDecimal(5000)),
      perTransaction = Some(BigDecimal(1000)),
      perCategory = Map(
        Category.Food -> BigDecimal(2000),
        Category.Entertainment -> BigDecimal(1000),
        Category.Education -> BigDecimal(3000)
      ),
      withdrawalLimits = WithdrawalLimits(
        daily = Some(BigDecimal(500)),
        weekly = Some(BigDecimal(2000)),
        monthly = Some(BigDecimal(5000))
      )
    ),
    payees = List(
      Payee(
        id = "payee_1",
        name = "Pizza Hut",
        accountNumber = "9876543210",
        trusted = true,
        addedAt = Instant.now()
      ),
      Payee(
        id = "payee_2", 
        name = "BookStore",
        accountNumber = "1234567890",
        trusted = true,
        addedAt = Instant.now()
      )
    ),
    transactions = List.empty
  )
  
  // Add account to repository
  repository.addAccount(demoAccount)
  
  // Create demo parent
  val demoParent = Parent(
    id = "parent_demo_789",
    name = "Demo Parent",
    email = "parent@demo.com",
    minorAccountIds = List(demoAccount.id)
  )
  repository.addParent(demoParent)

  override def start(): Unit = {
    
    // ===== MINOR DASHBOARD TAB =====
    
    // Balance display
    var currentAccount = demoAccount
    val balanceLabel = new Label {
      text = f"Current Balance: ₹${currentAccount.availableBalance}%.2f"
      style = "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #2e7d32;"
    }
    
    val totalBalanceLabel = new Label {
      text = f"Total Balance (incl. locked): ₹${currentAccount.totalBalance}%.2f"
      style = "-fx-font-size: 12px; -fx-text-fill: #666;"
    }
    
    // Monthly limit status
    val monthlySpent = repository.getMonthlySpending(currentAccount.id)
    val monthlyLimit = currentAccount.limits.monthly.getOrElse(BigDecimal(0))
    val limitStatusLabel = new Label {
      text = f"Monthly Spending: ₹$monthlySpent%.2f / ₹$monthlyLimit%.2f"
      style = "-fx-font-size: 14px;"
    }
    
    // Category limit display
    val categoryLimitLabel = new Label {
      val foodLimit = currentAccount.limits.perCategory.getOrElse(Category.Food, BigDecimal(0))
      text = f"Food Limit: ₹$foodLimit%.2f"
      style = "-fx-font-size: 12px; -fx-text-fill: #555;"
    }
    
    // Transaction table
    val transactionBuffer = ObservableBuffer[Transaction]()
    val transactionTable = new TableView[Transaction](transactionBuffer) {
      columns ++= List(
        new TableColumn[Transaction, String] {
          text = "Amount"
          cellValueFactory = t => s"₹${t.value.amount}"
          prefWidth = 100
        },
        new TableColumn[Transaction, String] {
          text = "Category"
          cellValueFactory = t => t.value.category.toString
          prefWidth = 120
        },
        new TableColumn[Transaction, String] {
          text = "Status"
          cellValueFactory = t => t.value.status.toString
          prefWidth = 150
        },
        new TableColumn[Transaction, String] {
          text = "Time"
          cellValueFactory = t => t.value.timestamp.toString.substring(11, 19)
          prefWidth = 100
        }
      )
      prefHeight = 200
    }
    
    // Category selector
    val categoryChoice = new ChoiceBox[Category] {
      items = ObservableBuffer(Category.Food, Category.Entertainment, Category.Education, Category.Travel)
      value = Category.Food
    }
    
    // Amount field
    val amountField = new TextField {
      promptText = "Enter amount (₹)"
      prefWidth = 150
    }
    
    // Make transaction button
    val makeTransactionButton = new Button("Make Transaction") {
      style = "-fx-background-color: #1976d2; -fx-text-fill: white; -fx-font-size: 14px;"
      onAction = handle {
        try {
          val amount = BigDecimal(amountField.text())
          val category = categoryChoice.value()
          
          val transaction = Transaction(
            id = UUID.randomUUID().toString,
            fromAccountId = currentAccount.id,
            toPayeeId = Some(currentAccount.payees.head.id),
            amount = amount,
            category = category,
            timestamp = Instant.now(),
            status = TransactionStatus.Pending
          )
          
          // Process through your TransactionController
          transactionController.processTransaction(currentAccount.id, transaction) match {
            case Right(updatedAccount) =>
              currentAccount = updatedAccount
              balanceLabel.text = f"Current Balance: ₹${currentAccount.availableBalance}%.2f"
              totalBalanceLabel.text = f"Total Balance (incl. locked): ₹${currentAccount.totalBalance}%.2f"
              
              val newMonthlySpent = repository.getMonthlySpending(currentAccount.id)
              limitStatusLabel.text = f"Monthly Spending: ₹$newMonthlySpent%.2f / ₹$monthlyLimit%.2f"
              
              transactionBuffer.clear()
              transactionBuffer ++= repository.getTransactions(currentAccount.id).take(10)
              
              amountField.text = ""
              showAlert("Success", f"Transaction of ₹$amount%.2f approved!", Alert.AlertType.Information)
              
            case Left(error) =>
              showAlert("Transaction Blocked", error, Alert.AlertType.Warning)
          }
        } catch {
          case _: NumberFormatException =>
            showAlert("Invalid Input", "Please enter a valid amount", Alert.AlertType.Error)
          case e: Exception =>
            showAlert("Error", s"Transaction failed: ${e.getMessage}", Alert.AlertType.Error)
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
          new Label("Youth Banking - Minor Account") {
            style = "-fx-font-size: 18px; -fx-font-weight: bold;"
          },
          new Separator(),
          balanceLabel,
          totalBalanceLabel,
          limitStatusLabel,
          categoryLimitLabel,
          new Separator(),
          new Label("Make a Transaction:") {
            style = "-fx-font-size: 14px; -fx-font-weight: bold;"
          },
          new HBox {
            spacing = 10
            children = Seq(
              new Label("Category:"),
              categoryChoice,
              new Label("Amount:"),
              amountField,
              makeTransactionButton
            )
          },
          new Label("Recent Transactions:") {
            style = "-fx-font-size: 14px; -fx-font-weight: bold;"
          },
          transactionTable
        )
      }
    }
    
    // ===== PARENT DASHBOARD TAB =====
    
    val pendingApprovalsBuffer = ObservableBuffer[Transaction]()
    val pendingApprovalsTable = new TableView[Transaction](pendingApprovalsBuffer) {
      columns ++= List(
        new TableColumn[Transaction, String] {
          text = "Amount"
          cellValueFactory = t => f"₹${t.value.amount}%.2f"
          prefWidth = 100
        },
        new TableColumn[Transaction, String] {
          text = "Category"
          cellValueFactory = t => t.value.category.toString
          prefWidth = 120
        },
        new TableColumn[Transaction, String] {
          text = "Status"
          cellValueFactory = t => t.value.status.toString
          prefWidth = 150
        }
      )
      prefHeight = 200
    }
    
    val refreshApprovalsButton = new Button("Refresh Pending") {
      onAction = handle {
        pendingApprovalsBuffer.clear()
        val pending = repository.getTransactions(currentAccount.id)
          .filter(_.status == TransactionStatus.RequiresParentApproval)
        pendingApprovalsBuffer ++= pending
      }
    }
    
    val approveButton = new Button("Approve Selected") {
      style = "-fx-background-color: #2e7d32; -fx-text-fill: white;"
      onAction = handle {
        val selected = pendingApprovalsTable.selectionModel().getSelectedItem
        if (selected != null) {
          // Update transaction status
          repository.updateTransactionStatus(selected.id, TransactionStatus.Completed)
          pendingApprovalsBuffer.remove(selected)
          showAlert("Approved", s"Transaction ${selected.id.take(8)}... approved!", Alert.AlertType.Information)
        } else {
          showAlert("No Selection", "Please select a transaction to approve", Alert.AlertType.Warning)
        }
      }
    }
    
    val parentTab = new Tab {
      text = "Parent Dashboard"
      closable = false
      content = new VBox {
        padding = Insets(20)
        spacing = 15
        children = Seq(
          new Label("Parent Control Panel") {
            style = "-fx-font-size: 18px; -fx-font-weight: bold;"
          },
          new Separator(),
          new Label(s"Managing Account: ${currentAccount.id}"),
          new Label(s"Minor: ${currentAccount.minorId}"),
          new Separator(),
          new Label("Pending Approvals:") {
            style = "-fx-font-size: 14px; -fx-font-weight: bold;"
          },
          pendingApprovalsTable,
          new HBox {
            spacing = 10
            children = Seq(refreshApprovalsButton, approveButton)
          }
        )
      }
    }
    
    // ===== AI RISK TAB =====
    
    val riskScoreLabel = new Label("Risk Score: -- / 100") {
      style = "-fx-font-size: 16px; -fx-font-weight: bold;"
    }
    
    val riskLevelLabel = new Label("Risk Level: --") {
      style = "-fx-font-size: 14px;"
    }
    
    val runRiskCheckButton = new Button("Run AI Risk Analysis") {
      style = "-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 14px;"
      onAction = handle {
        try {
          val amount = BigDecimal(500)
          val testTx = Transaction(
            fromAccountId = currentAccount.id,
            toPayeeId = Some("unknown_payee"),
            amount = amount,
            category = Category.Entertainment
          )
          
          val riskScore = aiRiskEngine.calculateRiskScore(currentAccount, testTx)
          riskScoreLabel.text = f"Risk Score: ${riskScore.score}%.1f / 100"
          riskLevelLabel.text = s"Risk Level: ${riskScore.level}"
          riskLevelLabel.style = riskScore.level match {
            case RiskLevel.Low => "-fx-font-size: 14px; -fx-text-fill: green;"
            case RiskLevel.Medium => "-fx-font-size: 14px; -fx-text-fill: orange;"
            case RiskLevel.High => "-fx-font-size: 14px; -fx-text-fill: red;"
            case RiskLevel.Critical => "-fx-font-size: 14px; -fx-text-fill: darkred; -fx-font-weight: bold;"
          }
          
          showAlert("AI Analysis Complete", 
            f"Risk Score: ${riskScore.score}%.1f\nLevel: ${riskScore.level}\nReasons: ${riskScore.reasons.mkString(", ")}", 
            Alert.AlertType.Information)
        } catch {
          case e: Exception =>
            showAlert("Error", s"Risk analysis failed: ${e.getMessage}", Alert.AlertType.Error)
        }
      }
    }
    
    val aiRiskTab = new Tab {
      text = "AI Risk Monitor"
      closable = false
      content = new VBox {
        padding = Insets(20)
        spacing = 15
        children = Seq(
          new Label("AI-Powered Risk Analysis") {
            style = "-fx-font-size: 18px; -fx-font-weight: bold;"
          },
          new Separator(),
          riskScoreLabel,
          riskLevelLabel,
          runRiskCheckButton,
          new Separator(),
          new Label("Real-time fraud detection and behavioral analysis") {
            style = "-fx-font-size: 12px; -fx-text-fill: #666;"
          }
        )
      }
    }
    
    // ===== MAIN STAGE =====
    
    stage = new JFXApp3.PrimaryStage {
      title = "Youth Banking - Demo System"
      width = 800
      height = 600
      scene = new Scene {
        root = new TabPane {
          tabs = Seq(minorTab, parentTab, aiRiskTab)
        }
      }
    }
  }
  
  // Helper method for alerts
  def showAlert(title: String, message: String, alertType: Alert.AlertType = Alert.AlertType.Information): Unit = {
    new Alert(alertType) {
      initOwner(stage)
      headerText = title
      contentText = message
    }.showAndWait()
  }
}
