package scalaz
package typelevel

import scalaz.{Apply, Kleisli}

sealed trait GenericList[+M[_]] {

  type Transformed[N[_]] <: GenericList[N]
  type Folded[N[X] >: M[X], U, F <: HFold[N, U]] <: U
  type Appended[N[X] >: M[X], L <: GenericList[N]] <: GenericList[N]
  type Function[R]
  type Down <: GenericList[Id]

  def transform[N[_]](trans: M ~> N): Transformed[N]
  def fold[N[X] >: M[X], U, F <: HFold[N, U]](fold: F): Folded[N, U, F]
  def append[N[X] >: M[X], L <: GenericList[N]](list: L): Appended[N, L]
  def apply[N[X] >: M[X] : Apply, R](f: N[Function[R]]): N[R]
  def down: Down

  final def coerce[N[X] >: M[X]]: Transformed[N] = {
    val t = new (M ~> N) {
      def apply[A](ma: M[A]): N[A] = ma
    }
    transform(t)
  }
 
  final def :^:[A, N[X] >: M[X]](elem: N[A]): GenericCons[N, A, this.type] =
    GenericCons[N, A, this.type](elem, this)

}

case class GenericCons[M[_], H, +T <: GenericList[M]](
  head: M[H],
  tail: T
) extends GenericList[M] {

  override type Transformed[N[_]] = GenericCons[N, H, tail.Transformed[N]]
  override type Folded[N[X] >: M[X], U, F <: HFold[N, U]] = F#Apply[H, tail.Folded[N, U, F]]
  override type Appended[N[X] >: M[X], L <: GenericList[N]] = GenericCons[N, H, tail.Appended[N, L]]
  override type Function[R] = H => tail.Function[R]
  override type Down = GenericCons[Id, M[H], tail.Down]

  def transform[N[_]](trans: M ~> N) = GenericCons(trans(head), tail.transform(trans))
  def fold[N[X] >: M[X], U, F <: HFold[N, U]](f: F): Folded[N, U, F] = f(head, tail.fold[N, U, F](f))
  def append[N[X] >: M[X], L <: GenericList[N]](list: L) = GenericCons[N, H, tail.Appended[N, L]](head, tail.append[N, L](list))
  def apply[N[X] >: M[X] : Apply, R](f: N[Function[R]]): N[R] = tail.apply(Apply[N].ap(head)(f))
  def down: Down = GenericCons[Id, M[H], tail.Down](head, tail.down)

}

case class GenericNil[M[_]]() extends GenericList[M] {

  override type Transformed[N[_]] = GenericNil[N]
  override type Folded[N[X] >: M[X], U, F <: HFold[N, U]] = F#Init
  override type Appended[N[X] >: M[X], L <: GenericList[N]] = L
  override type Function[R] = R
  override type Down = GenericNil[Id]

  def transform[N[_]](trans: M ~> N) = GenericNil()
  def fold[N[X] >: M[X], U, F <: HFold[N, U]](fold: F): Folded[N, U, F] = fold.init
  def append[N[X] >: M[X], L <: GenericList[N]](list: L) = list
  def apply[N[X] >: M[X] : Apply, R](f: N[Function[R]]): N[R] = f
  def down: Down = GenericNil[Id]()

}

trait GenericLists extends HLists {

  // Kleisli proofs

  import Kleisli._

  sealed trait Direction
  final class Forward extends Direction
  final class Reverse extends Direction

  sealed trait KleisliProof[D <: Direction, M[_], H, R, T <: HList] {
    def apply(list: T)(implicit b: Bind[M]): Kleisli[M, H, R]
  }

  implicit def baseKleisliProof[D <: Direction, M[_], H, R]: KleisliProof[D, M, H, R, HCons[H => M[R], HNil]] = 
    new KleisliProof[D, M, H, R, HCons[H => M[R], HNil]] {
      def apply(list: HCons[H => M[R], HNil])(implicit b: Bind[M]) = kleisli(list.head)
    }

  implicit def consKleisliRevProof[M[_], OH, IH, R, T <: HList](
    implicit proof: KleisliProof[Reverse, M, IH, R, T]
  ): KleisliProof[Reverse, M, OH, R, HCons[OH => M[IH], T]] = 
    new KleisliProof[Reverse, M, OH, R, HCons[OH => M[IH], T]] {
      def apply(list: HCons[OH => M[IH], T])(implicit b: Bind[M]) = kleisli(list.head) >=> proof(list.tail)
    }

  implicit def consKleisliProof[M[_], H, OR, IR, T <: HList](
    implicit proof: KleisliProof[Forward, M, H, IR, T]
  ): KleisliProof[Forward, M, H, OR, HCons[IR => M[OR], T]] = 
    new KleisliProof[Forward, M, H, OR, HCons[IR => M[OR], T]] {
      def apply(list: HCons[IR => M[OR], T])(implicit b: Bind[M]) = kleisli(list.head) <=< proof(list.tail)
    }

}

object GenericLists extends GenericLists

// vim: expandtab:ts=2:sw=2

