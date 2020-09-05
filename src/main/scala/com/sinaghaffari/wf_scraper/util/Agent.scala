package com.sinaghaffari.wf_scraper.util

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

case class Agent[T: ClassTag](initValue: T)(implicit system: ActorSystem) {
  private val actor: ActorRef = system.actorOf(Props(new this.AgentActor()))
  private implicit val timeout: Timeout = Timeout(1.minute)
  actor ! SetValue(initValue)

  def apply(): Future[T] = {
    (actor ? GetValue).mapTo[T]
  }

  def send(v: T): Future[T] = {
    (actor ? SetValue(v)).mapTo[T]
  }

  def send(f: T => T): Future[T] = {
    (actor ? ModValue(f)).mapTo[T]
  }

  def sendAsync(v: Future[T]): Future[T] = {
    import system.dispatcher
    v.flatMap(a => (actor ? SetValue(a)).mapTo[T])
  }

  def sendAsync(f: T => Future[T]): Future[T] = {
    import system.dispatcher
    (actor ? GetValue).mapTo[T].flatMap(f)
  }

  private case class SetValue(value: T)

  private case class ModValue(f: T => T)

  private class AgentActor extends Actor {
    var v: Option[T] = None

    override def receive: Receive = {
      case SetValue(a) =>
        v = Some(a)
        sender() ! v.get
      case ModValue(f) =>
        v = Some(f(v.get))
        sender() ! v.get
      case GetValue => sender() ! v.get
    }
  }

  private case object GetValue

}
