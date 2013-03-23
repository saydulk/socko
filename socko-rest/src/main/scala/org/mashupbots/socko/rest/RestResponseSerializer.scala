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
package org.mashupbots.socko.rest

import scala.reflect.runtime.{ universe => ru }
import org.mashupbots.socko.infrastructure.ReflectUtil
import java.util.Date
import java.text.SimpleDateFormat
import org.mashupbots.socko.infrastructure.DateUtil
import java.io.InputStream
import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.infrastructure.CharsetUtil
import org.json4s.native.{ Serialization => json }
import org.json4s.NoTypeHints
import org.mashupbots.socko.infrastructure.IOUtil

/**
 * Seralized outgoing data from a [[org.mashupbots.socko.rest.RestResposne]]
 *
 * @param responseClass Response class symbol
 */
case class RestResponseSerializer(
  responseClass: ru.ClassSymbol,
  responseDataType: ResponseDataType.Value,
  responseDataTerm: Option[ru.TermSymbol],
  rm: ru.Mirror) {

  /**
   * Returns the data object for an instance of the response
   *
   * @returns `None` if the response data type is void, else the value of the data field
   */
  def getData(response: RestResponse): Option[Any] = {
    if (responseDataType == ResponseDataType.Void) None
    else {
      val instanceMirror = rm.reflect(response)
      val fieldMirror = instanceMirror.reflectField(responseDataTerm.get)
      Some(fieldMirror.get)
    }
  }

  /**
   * Serialize the data to a HTTP response
   *
   * @param http HTTP event
   * @param response Response object to serailize
   */
  def serialize(http: HttpRequestEvent, response: RestResponse) {
    responseDataType match {
      case ResponseDataType.Void => {
        http.response.write(response.context.status, Array.empty[Byte], "", response.context.headers)
      }
      case ResponseDataType.Primitive => {
        val data = getData(response)
        val bytes = if (data.isDefined) {
          data.get.toString.getBytes(CharsetUtil.UTF_8)
        } else {
          Array.empty[Byte]
        }
        http.response.write(response.context.status, bytes, "text/plain; charset=UTF-8", response.context.headers)
      }
      case ResponseDataType.Object => {
        val data = getData(response)
        val bytes = if (data.isDefined) {
          implicit val formats = json.formats(NoTypeHints)
          val s = json.write(data.get.asInstanceOf[AnyRef])
          s.getBytes(CharsetUtil.UTF_8)
        } else {
          Array.empty[Byte]
        }
        http.response.write(response.context.status, bytes, "application/json; charset=UTF-8", response.context.headers)
      }
      case ResponseDataType.InputStream => {
        val data = getData(response)
        val bytes = if (data.isDefined) {
          // TODO chunk it
          IOUtil.readInputStream(data.get.asInstanceOf[InputStream])
        } else {
          Array.empty[Byte]
        }
        http.response.write(response.context.status, bytes, "application/json; charset=UTF-8", response.context.headers)
      }
      case _ => { 
        throw new IllegalStateException(s"Unsupported ResponseDataType ${responseDataType.toString}")
      }
    }
  }

}

/**
 * Companion object
 */
object RestResponseSerializer {

  private val primitiveTypes: Map[ru.Type, (Any) => String] = Map(
    (ru.typeOf[String], (s: Any) => s.toString),
    (ru.typeOf[Int], (s: Any) => s.toString),
    (ru.typeOf[Boolean], (s: Any) => s.toString),
    (ru.typeOf[Byte], (s: Any) => s.toString),
    (ru.typeOf[Short], (s: Any) => s.toString),
    (ru.typeOf[Long], (s: Any) => s.toString),
    (ru.typeOf[Double], (s: Any) => s.toString),
    (ru.typeOf[Float], (s: Any) => s.toString),
    (ru.typeOf[Date], (s: Any) => DateUtil.formatISO8601DateTime(s.asInstanceOf[Date])))

  private val inputStreamType = ru.typeOf[InputStream]

  /**
   * Factory for RestResponseSerializer
   *
   * @param rm Runtime Mirror with the same class loaders as the specified request class
   * @param definition Definition of the operation
   * @param responseClassSymbol Response class symbol
   */
  def apply(rm: ru.Mirror, definition: RestOperationDef, responseClassSymbol: ru.ClassSymbol): RestResponseSerializer = {
    val responseConstructor: ru.MethodSymbol = responseClassSymbol.toType.declaration(ru.nme.CONSTRUCTOR).asMethod
    val responseConstructorParams: List[ru.TermSymbol] = responseConstructor.paramss(0).map(p => p.asTerm)

    if (responseConstructorParams.size == 0) {
      throw RestDefintionException(s"${responseClassSymbol.fullName} constructor must have parameters.")
    } else if (responseConstructorParams(0).name.toString() != "context") {
      throw RestDefintionException(s"First constructor parameter for ${responseClassSymbol.fullName} must be 'context'.")
    }

    val responseDataType = if (responseConstructorParams.size == 1) {
      ResponseDataType.Void
    } else {
      val contentTerm = responseConstructorParams(1)
      val contentType = contentTerm.typeSignature
      if (primitiveTypes.exists(t => t._1 =:= contentType)) {
        ResponseDataType.Primitive
      } else if (contentType <:< inputStreamType) {
        ResponseDataType.InputStream
      } else {
        ResponseDataType.Object
      }
    }

    // The data term name assumed to be in the constructor of a "case class"
    // Get the term name and reflect it as a field in order to read its value
    val responseDataTerm: Option[ru.TermSymbol] = if (responseDataType == ResponseDataType.Void) None else {
      val contentTerm = responseConstructorParams(1)
      Some(responseClassSymbol.toType.declaration(contentTerm.name).asTerm.accessed.asTerm)
    }

    RestResponseSerializer(responseClassSymbol, responseDataType, responseDataTerm, rm)
  }

}

object ResponseDataType extends Enumeration {
  type ResponseDataType = Value
  val Void, Primitive, Object, InputStream = Value
} 

  