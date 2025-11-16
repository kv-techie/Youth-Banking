package services

import domain._
import scala.collection.concurrent.TrieMap
import java.time.Instant

/** Lightweight in-memory repository used by services for demo/testing purposes.
  * Not thread-safe for heavy concurrency; suitable for a demo/small project.
  */
object Repository {
  // accounts keyed by id
  private val accounts = TrieMap.empty[String, Account]
  private val parents  = TrieMap.empty[String, Parent]
  private val minors   = TrieMap.empty[String, Minor]
  private val alerts   = TrieMap.empty[String, List[Alert]]

  // CRUD helpers
  def saveAccount(a: Account): Account = { accounts.update(a.id, a); a }
  def getAccount(id: String): Option[Account] = accounts.get(id)
  def updateAccount(id: String)(fn: Account => Account): Option[Account] =
    accounts.get(id).map(a => { val na = fn(a); accounts.update(id, na); na })

  def saveParent(p: Parent): Parent = { parents.update(p.id, p); p }
  def getParent(id: String): Option[Parent] = parents.get(id)

  def saveMinor(m: Minor): Minor = { minors.update(m.id, m); m }
  def getMinor(id: String): Option[Minor] = minors.get(id)

  def addAlert(accountId: String, alert: Alert): Unit = {
    val existing = alerts.getOrElse(accountId, Nil)
    alerts.update(accountId, alert :: existing)
  }
  def getAlerts(accountId: String): List[Alert] = alerts.getOrElse(accountId, Nil)

  // convenience to create demo accounts
  def clearAll(): Unit = {
    accounts.clear(); parents.clear(); minors.clear(); alerts.clear()
  }
}
