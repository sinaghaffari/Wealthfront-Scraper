package com.sinaghaffari.wf_scraper.util

import scala.concurrent.{ExecutionContext, Future}

object MonadicSimplifier {

  class FutureEitherOps[A](futureEither: Future[Either[Throwable, A]]) {
    def ?| : Step[A] = Step(futureEither)
  }

  final case class Step[+A](run: Future[Either[Throwable, A]]) {
    def map[B](f: A => B)(implicit ec: ExecutionContext): Step[B] =
      copy(run = run.map(_.map(f)))

    def flatMap[B](f: A => Step[B])(implicit ec: ExecutionContext): Step[B] =
      copy(run = run.flatMap(_.fold(
        err => Future.successful(Left[Throwable, B](err)),
        succ => f(succ).run
      )))

    def withFilter(p: A => Boolean)(implicit ec: ExecutionContext): Step[A] =
      copy(run = run.filter {
        case Right(a) if p(a) => true
        case Left(e) => true
        case _ => false
      })
  }

  implicit def simplifyFutureEither[A](futureEither: Future[Either[Throwable, A]]): FutureEitherOps[A] = new
      FutureEitherOps(futureEither)
}
