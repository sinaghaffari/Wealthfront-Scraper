package com.sinaghaffari.wf_scraper.models.alpha_vantage

import java.io.{BufferedWriter, File, FileWriter}

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, RunnableGraph, Sink, Source,
  SourceQueueWithComplete}
import akka.stream.{ClosedShape, OverflowStrategy}
import com.typesafe.config.Config
import play.api.libs.json.{JsObject, Json, OWrites, Reads}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

case class AssetOverview(Symbol: String, AssetType: String, Name: String, Exchange: String, Currency: String,
                         Country: String, Sector: String, Industry: String)

object AssetOverview {
  implicit val assetOverviewReads: Reads[AssetOverview] = Json.reads[AssetOverview]
  private val assetOverviewWrites: OWrites[AssetOverview] = Json.writes[AssetOverview]
  implicit val assetOverviewSnakeCaseWrites: OWrites[AssetOverview] = Json.writes[AssetOverview]
    .transform((a: JsObject) =>
      JsObject(a.fields.map(x =>
        (
          x._1.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase,
          x._2
        )
      ))
    )

  case class AssetOverviewManager()(implicit ws: StandaloneAhcWSClient, actorSystem: ActorSystem, config: Config) {
    type Symbol2Asset = (String, Promise[AssetOverview])

    import actorSystem.dispatcher

    val assetOverviewFiles: Map[String, File] = new File("asset_overviews/")
      .listFiles()
      .map(file => (file.getName.split('.')(0), file))
      .toMap
      .withDefault { name =>
        val file = new File(s"asset_overviews/$name.json")
        file.createNewFile()
        file
      }
    val assetOverviews: Map[String, AssetOverview] = assetOverviewFiles.view
      .mapValues(io.Source.fromFile)
      .mapValues(_.mkString)
      .mapValues(Json.parse)
      .mapValues(_.as[AssetOverview])
      .toMap
    private val alphaVantageApiKey: String = config.getString("alpha_vantage.api_key")
    private val queue: SourceQueueWithComplete[Symbol2Asset] = {
      val queueSource: Source[Symbol2Asset, SourceQueueWithComplete[Symbol2Asset]] = Source.queue[Symbol2Asset](500,
        OverflowStrategy.backpressure)


      RunnableGraph.fromGraph(GraphDSL.create(queueSource) { implicit builder =>
        queue =>
          import GraphDSL.Implicits._
          val partitioner = builder.add(Partition[Symbol2Asset](2, s => if (assetOverviews.contains(s._1)) 0 else 1))
          val combiner = builder.add(Merge[(AssetOverview, Promise[AssetOverview])](2))
          val saveFileSplitter = builder.add(Broadcast[(AssetOverview, Promise[AssetOverview])](2))
          queue ~> partitioner.in
          partitioner.out(0) ~> Flow[Symbol2Asset].map(x => (assetOverviews(x._1), x._2)) ~> combiner.in(0)
          partitioner.out(1) ~> Flow[Symbol2Asset].throttle(3, 1.minute).mapAsync(1)(x => ws.url(s"https://www" +
            s".alphavantage.co/query")
            .withQueryStringParameters(
              "function" -> "OVERVIEW",
              "symbol" -> x._1,
              "apikey" -> alphaVantageApiKey
            )
            .get()
            .map { res =>
              println(s"Alpha Vantage Request for ${x._1} | ${res.status} ${res.statusText}")
              println(s"Body:\n${res.body}")
              println
              res
            }
            .map(_.body)
            .map(Json.parse)
            .map(_.as[AssetOverview])
            .map((_, x._2))) ~> saveFileSplitter.in
          saveFileSplitter.out(0) ~> combiner.in(1)
          saveFileSplitter.out(1) ~> Flow[(AssetOverview, Promise[AssetOverview])].map(_._1).map(x => (Json
            .prettyPrint(Json.toJson(x)(assetOverviewWrites)), assetOverviewFiles(x.Symbol))) ~> Sink.foreach[
            (String, File)] {
            case (json, file) =>
              val pw = new BufferedWriter(new FileWriter(file))
              pw.write(json)
              pw.close()
          }
          combiner.out ~> Sink.foreach[(AssetOverview, Promise[AssetOverview])](x => x._2.success(x._1))
          ClosedShape
      }).run()
    }

    def getAssetOverview(symbol: String): Future[AssetOverview] = {
      val resultPromise: Promise[AssetOverview] = Promise[AssetOverview]()
      for {
        _ <- queue.offer((symbol, resultPromise))
        result <- resultPromise.future
      } yield result
    }
  }

}
