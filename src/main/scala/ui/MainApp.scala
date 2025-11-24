import scalafx.application.JFXApp3
import scalafx.scene.Scene
import scalafx.scene.control.{TabPane, Tab, Button, Label, TableView}
import scalafx.scene.layout.{VBox, HBox}

object MainApp extends JFXApp3 {
  override def start(): Unit = {
    stage = new JFXApp3.PrimaryStage {
      title = "Youth Banking Demo"
      scene = new Scene(600, 400) {
        root = new TabPane {
          tabs = Seq(
            new Tab {
              text = "Minor Dashboard"
              content = new VBox {
                children = Seq(new Label("Balance: ₹[dynamic]"),
                  new Button("Make Transaction"))
              }
            },
            new Tab {
              text = "Parent Dashboard"
              content = new VBox {
                children = Seq(new Label("Pending Approvals: [dynamic]"),
                  new Button("Approve"))
              }
            },
            new Tab {
              text = "AI Risk"
              content = new VBox {
                children = Seq(new Label("Risk Score: [dynamic]"))
              }
            }
          )
        }
      }
    }
  }
}
