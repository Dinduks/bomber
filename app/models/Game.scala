package models.game

import akka.actor._
import scala.concurrent.duration._

import play.api._
import play.api.libs.iteratee._
import play.api.libs.iteratee.Concurrent._
import play.api.libs.concurrent._
import play.api.mvc.RequestHeader

import play.api.libs.json._

import akka.util.Timeout
import akka.pattern.ask

import scala.util.Random

import play.api.Play.current

class Game extends Actor {
  import models.commander.Commander._
  private var members = Map.empty[String, PlayerInfos]
  private val bomberStyles = List("classic", "punk", "robot", "miner")
  private val playerPositions = List(Coord(30,30), Coord(270,30), Coord(30,270), Coord(270,270))

  def receive = {
    case m:NewDirection => {
      broadcastBut(m.userId, m)
      (members get m.userId).get.position = m.position
    }
    case ("createPlayer", uId:String, channel:Channel[JsValue]) => createPlayer(uId, channel)
    case ("deletePlayer", userId:String) => {
      if(members contains userId) {
        context.stop((members get userId).get.actor)
        members -= userId
        broadcast(DeletePlayer(userId))
      }
    }
    case bomb:Bomb => broadcast(bomb)
  }

  def createPlayer(userId: String, out: Channel[JsValue]) = {
    val p_actor = context.actorOf(Props(new Player(out)), name="act_"+userId)

    members = members + (userId -> (new PlayerInfos(userId, bomberStyles(Random.nextInt(4)), p_actor, nextPlayerPosition)))

    broadcast(NewPlayer(userId, (members get userId).get.style))
    broadcast(Position(userId, (members get userId).get.position))
    sendPlayersList(userId)
    broadcast(Board(generateBoard(11,11)))
  }

  def generateBoard(w:Int, h:Int) = {
    val xr = 0 until w
    val yr = 0 until h

    (for (a <- xr; b <- yr) yield(a,b)).map {
      v => v match {
        case (0,y)            => Element(Coord(0,y), boardElem.WALL) // LEFT WALL
        case (x,0)            => Element(Coord(x,0), boardElem.WALL) // TOP WALL
        case (x,y) if(y==h-1) => Element(Coord(x,y), boardElem.WALL) // BOTTOM WALL
        case (x,y) if(x==w-1) => Element(Coord(x,y), boardElem.WALL) // RIGHT WALL
        case (x,y) => (x,y) match {
          case (x,y) if(x%2==0 && y%2==0)
              => Element(Coord(x,y), boardElem.WALL)
          case (x,y) if(!((x == 1 || x == 2) && (y == 1 || y == 2)) &&      // TOP-LEFT PLAYER SPAWN ZONE
                        !((x == w-2 || x == w-3) && (y == 1 || y == 2)) &&  // TOP-RIGHT PLAYER SPAWN ZONE
                        !((x == 1 || x == 2) && (y == h-2 || y == h-3)) &&  // BOTTOM-LEFT PLAYER SPAWN ZONE
                        !((x == w-2 || x == w-3) && (y == h-2 || y == h-3)) // BOTTOM-RIGHT PLAYER SPAWN ZONE
                        ) => Element(Coord(x,y), genCrate)
          case _ => Element(Coord(x,y), boardElem.GROUND)
        }
      }
    }.toList
  }

  def genCrate = Math.random() match {
    case v:Double if(v<0.2)  => boardElem.C_BOMB
    case v:Double if(v<0.4)  => boardElem.C_SPEED
    case v:Double            => boardElem.CRATE
    }

  def sendPlayersList(receiver:String) = {
    members filterKeys (_!=receiver) foreach {
      v => {
        (members get receiver).get.actor ! NewPlayer(v._2.userId, v._2.style)
        (members get receiver).get.actor ! Position(v._2.userId, v._2.position)
      }
    }
  }

  def nextPlayerPosition = {
    playerPositions(members.size)
  }

  def broadcast(msg: Message) {
    members.foreach(v => v._2.actor ! msg )
  }

  def broadcastBut(userId: String, msg: Message) {
    members.filter(v => v._1 != userId).foreach(v => v._2.actor ! msg )
  }
}

class PlayerInfos(val userId:String, val style:String, val actor:ActorRef, var position:Coord)

class Player(out: Channel[JsValue]) extends Actor {
  import models.commander.Commander._
  def receive = {
    case msg:Message => {
      msg match {
        case nd : NewDirection  =>
          out.push(
            Json.obj(
              "kind"-> "newDirection",
              "c"   -> Json.toJson(nd)
            )
          )
        case np : NewPlayer     =>
          out.push(
            Json.obj(
              "kind"-> "newPlayer",
              "c"   ->  Json.toJson(np)
            )
          )
        case pos : Position     =>
          out.push(
            Json.obj(
              "kind"-> "position",
              "c"   ->  Json.obj(
                "userId"  ->  pos.userId,
                "x"       ->  pos.pos.x,
                "y"       ->  pos.pos.y
                )
            )
          )
        case dp : DeletePlayer =>
          out.push(
            Json.obj(
              "kind"  -> "deletePlayer",
              "c"     ->  Json.toJson(dp)
            )
          )
        case bo : Board =>
          out.push(
            Json.obj(
              "kind"  -> "board",
              "c"     ->  Json.toJson(bo)
            )
          )
        case bomb : Bomb =>
          out.push(
            Json.obj(
              "kind"  -> "bomb",
              "c"     ->  Json.toJson(bomb)
            )
          )
      }
    }
  }
}

abstract class Message
case class NewDirection(userId:String, x:Int, y:Int, position:Coord) extends Message
case class NewPlayer(userId:String, style:String) extends Message
case class Position(userId:String, pos:Coord) extends Message
case class DeletePlayer(userId:String) extends Message
case class Bomb(userId:String, name:String, x:Int, y:Int, flameSize:Int, flameTime:Int) extends Message

case class Coord(x:Int, y:Int)
case class Element(coord:Coord, kind:Int)
case class Board(elements:List[Element]) extends Message

object boardElem {
  val GROUND  = 0
  val WALL    = 1
  val CRATE   = 2     // CRATE WITH NO BONUS
  val C_BOMB  = 21    // CRATE WITH BOMB BONUS
  val C_SPEED  = 22   // CRATE WITH SPEED BONUS
}
