//
// Copyright 2013 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.examples.rest

import java.util.Date
import org.mashupbots.socko.rest.AllowableValuesList
import org.mashupbots.socko.rest.BodyParam
import org.mashupbots.socko.rest.Method
import org.mashupbots.socko.rest.PathParam
import org.mashupbots.socko.rest.RestModelMetaData
import org.mashupbots.socko.rest.RestPropertyMetaData
import org.mashupbots.socko.rest.RestRegistration
import org.mashupbots.socko.rest.RestRequest
import org.mashupbots.socko.rest.RestRequestContext
import org.mashupbots.socko.rest.RestResponse
import org.mashupbots.socko.rest.RestResponseContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import scala.collection.mutable.ListBuffer

//*************************************************************
// Model
//*************************************************************
case class Order(
  id: Long,
  petId: Long,
  status: String,
  quantity: Int,
  shipDate: Date)
object Order extends RestModelMetaData {
  val modelProperties = Seq(
    RestPropertyMetaData("status", "Order Status", Some(AllowableValuesList(List("placed", " approved", " delivered")))))
}

//*************************************************************
// Data
//*************************************************************
object StoreData {
  val orders: ListBuffer[Order] = new ListBuffer[Order]()

  orders += createOrder(1, 1, 2, new Date(), "placed")
  orders += createOrder(2, 1, 2, new Date(), "delivered")
  orders += createOrder(3, 2, 2, new Date(), "placed")
  orders += createOrder(4, 2, 2, new Date(), "delivered")
  orders += createOrder(5, 3, 2, new Date(), "placed")

  def findOrderById(orderId: Long): Order = {
    for (order <- orders) {
      if (order.id == orderId) {
        return order
      }
    }
    null
  }

  def placeOrder(order: Order): Unit = {
    // remove any pets with same id
    orders --= orders.filter(o => o.id == order.id)
    orders += order
  }

  def deleteOrder(orderId: Long): Unit = {
    orders --= orders.filter(o => o.id == orderId)
  }

  private def createOrder(id: Long, petId: Long, quantity: Int, shipDate: Date, status: String): Order = {
    val order = Order(id, petId, status, quantity, shipDate)
    order
  }
}

//*************************************************************
// API
//*************************************************************
case class GetOrderRequest(context: RestRequestContext, orderId: String) extends RestRequest
case class GetOrderResponse(context: RestResponseContext, order: Option[Order]) extends RestResponse
object GetOrderRegistration extends RestRegistration {
  val method = Method.GET
  val path = "/store/order/{orderId}"
  val requestParams = Seq(PathParam("orderId"))
  def processorActor(actorSystem: ActorSystem, request: RestRequest): ActorRef = null
}

case class DeleteOrderRequest(context: RestRequestContext, orderId: String) extends RestRequest
case class DeleteOrderResponse(context: RestResponseContext) extends RestResponse
object DeleteOrderRegistration extends RestRegistration {
  val method = Method.DELETE
  val path = "/store/order/{orderId}"
  val requestParams = Seq(PathParam("orderId"))
  def processorActor(actorSystem: ActorSystem, request: RestRequest): ActorRef = null
}

case class PlaceOrderRequest(context: RestRequestContext, order: Order) extends RestRequest
case class PlaceOrderResponse(context: RestResponseContext) extends RestResponse
object PlaceOrderRegistration extends RestRegistration {
  val method = Method.POST
  val path = "/store/order"
  val requestParams = Seq(BodyParam("order"))
  def processorActor(actorSystem: ActorSystem, request: RestRequest): ActorRef = null
}


