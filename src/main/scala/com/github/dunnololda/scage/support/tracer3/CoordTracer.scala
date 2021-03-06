package com.github.dunnololda.scage.support.tracer3

import com.github.dunnololda.scage.support.Vec
import com.github.dunnololda.cli.AppProperties._
import com.github.dunnololda.mysimplelogger.MySimpleLogger
import com.github.dunnololda.scage.handlers.RendererLib

class CoordTracer[T <: TraceTrait](field_from_x:Int   = property("field.from.x", 0),
                              field_to_x:Int          = property("field.to.x", try {RendererLib.windowWidth} catch {case e:Exception => 800}),
                              field_from_y:Int        = property("field.from.y", 0),
                              field_to_y:Int          = property("field.to.y", try {RendererLib.windowHeight} catch {case e:Exception => 600}),
                              init_h_x:Int            = property("field.h_x", 0),
                              init_h_y:Int            = property("field.h_y", 0),
                              init_N_x:Int            = if(property("field.h_x", 0) == 0) property("field.N_x", (try {RendererLib.windowWidth} catch {case e:Exception => 800})/50) else 0,
                              init_N_y:Int            = if(property("field.h_y", 0) == 0) property("field.N_y", (try {RendererLib.windowHeight} catch {case e:Exception => 600})/50) else 0,
                              solid_edges:Boolean = property("field.solid_edges", true))
extends ScageTracer[T](field_from_x,field_to_x,field_from_y,field_to_y,init_h_x,init_h_y,init_N_x,init_N_y,solid_edges) {
  private val log = MySimpleLogger(this.getClass.getName)

  override def addTrace(coord:Vec, trace:T):T = {
    if(isCoordOnArea(coord)) {
      trace._location = coord
      val p = point(coord)
      point_matrix(p.ix)(p.iy) += trace
      traces_by_ids += trace.id -> trace
      traces_list += trace
      log.debug("added new trace #"+trace.id+" in coord "+trace.location)
    } else log.warn("failed to add trace: coord "+trace.location+" is out of area")
    trace
  }

  override protected def _removeTrace(trace_id:Int, show_warn:Boolean) {
    traces_by_ids.get(trace_id) match {
      case Some(trace) =>
        val trace_point = point(trace.location)
        point_matrix(trace_point.ix)(trace_point.iy) -= trace
        traces_by_ids -= trace.id
        traces_list -= trace
        log.debug("removed trace #"+trace.id)
      case None => if(show_warn) log.warn("trace #"+trace_id+" not found")
    }
  }

  def tracesNearCoord(coord:Vec, xrange:Range, yrange:Range, condition:T => Boolean):IndexedSeq[T] = {
    val p = point(coord)
    super.tracesNearPoint(p, xrange, yrange, condition)
  }
  def tracesNearCoord(coord:Vec, xrange:Range, condition:T => Boolean):IndexedSeq[T] = tracesNearCoord(coord, xrange, xrange, condition)
  def tracesNearCoord(coord:Vec, xrange:Range, yrange:Range):IndexedSeq[T] = {
    val p = point(coord)
    super.tracesNearPoint(p, xrange, yrange)
  }
  def tracesNearCoord(coord:Vec, xrange:Range):IndexedSeq[T] = tracesNearCoord(coord:Vec, xrange:Range, xrange:Range)

  override def updateLocation(trace_id:Int, new_coord:Vec):Int = { // TODO: maybe return tuple (new_location, operation_status)
    traces_by_ids.get(trace_id) match {
      case Some(trace) =>
        val old_coord = trace.location
        val old_point = point(old_coord)
        val new_coord_edges_affected = outsideCoord(new_coord)
        if(isCoordOnArea(new_coord_edges_affected)) {
          val new_point_edges_affected = point(new_coord_edges_affected)
          if(old_coord != new_coord_edges_affected) {
            point_matrix(old_point.ix)(old_point.iy) -= trace
            point_matrix(new_point_edges_affected.ix)(new_point_edges_affected.iy) += trace
            trace._location = new_coord_edges_affected
            LOCATION_UPDATED
          } else {
            //log.warn("didn't update trace "+trace.id+": new point is the same as the old one")  // don'tknow exactly if I need such debug message
            SAME_LOCATION
          }
        } else {
          log.debug("failed to update trace "+trace_id+": new point is out of area")
          OUT_OF_AREA
        }
      case None =>
        log.warn("trace with id "+trace_id+" not found")
        TRACE_NOT_FOUND
    }
  }

  def outsideCoord(coord:Vec):Vec = {
    def checkC(c:Float, from:Float, to:Float):Float = {
      val dist = to - from
      if(c >= to) checkC(c - dist, from, to)
      else if(c < from) checkC(c + dist, from, to)
      else c
    }

    if(solid_edges) coord else {
      val x = checkC(coord.x, field_from_x, field_to_x)
      val y = checkC(coord.y, field_from_y, field_to_y)

      Vec(x, y)
    }
  }

  def isCoordOnArea(coord:Vec):Boolean = {
    coord.x >= field_from_x && coord.x < field_to_x && coord.y >= field_from_y && coord.y < field_to_y
  }

  def hasCollisions(target_trace_id:Int, tested_coord:Vec, min_dist:Float, condition:T => Boolean = (trace) => true):Boolean = {
    if(solid_edges && !isCoordOnArea(tested_coord)) true
    else {
      val tested_coord_edges_affected = outsideCoord(tested_coord)
      val min_dist2 = min_dist*min_dist
      val modified_condition = (trace:T) => trace.id != target_trace_id && condition(trace)

      val xrange = (2*min_dist/h_x).toInt + 1
      val yrange = (2*min_dist/h_y).toInt + 1
      tracesNearCoord(tested_coord_edges_affected, -xrange to xrange, -yrange to yrange, modified_condition)
        .exists(trace => (trace.location dist2 tested_coord_edges_affected) < min_dist2)
    }
  }
}

object CoordTracer {
  def apply(field_from_x:Int        = property("field.from.x", 0),
            field_to_x:Int          = property("field.to.x", try {RendererLib.windowWidth} catch {case e:Exception => 800}),
            field_from_y:Int        = property("field.from.y", 0),
            field_to_y:Int          = property("field.to.y", try {RendererLib.windowHeight} catch {case e:Exception => 600}),
            init_h_x:Int            = property("field.h_x", 0),
            init_h_y:Int            = property("field.h_y", 0),
            init_N_x:Int            = if(property("field.h_x", 0) == 0) property("field.N_x", (try {RendererLib.windowWidth} catch {case e:Exception => 800})/50) else 0,
            init_N_y:Int            = if(property("field.h_y", 0) == 0) property("field.N_y", (try {RendererLib.windowHeight} catch {case e:Exception => 600})/50) else 0,
            solid_edges:Boolean     = property("field.solid_edges", true)) = {
    new CoordTracer[Trace](field_from_x,field_to_x,field_from_y,field_to_y,init_h_x,init_h_y,init_N_x,init_N_y,solid_edges) {
      def addTrace(coord:Vec):Trace = {addTrace(coord, Trace())}
    }
  }

  // maybe some other name for this factory method (like 'newTracer', etc)
  def create[T <: TraceTrait](field_from_x:Int   = property("field.from.x", 0),
                         field_to_x:Int          = property("field.to.x", try {RendererLib.windowWidth} catch {case e:Exception => 800}),
                         field_from_y:Int        = property("field.from.y", 0),
                         field_to_y:Int          = property("field.to.y", try {RendererLib.windowHeight} catch {case e:Exception => 600}),
                         init_h_x:Int            = property("field.h_x", 0),
                         init_h_y:Int            = property("field.h_y", 0),
                         init_N_x:Int            = if(property("field.h_x", 0) == 0) property("field.N_x", (try {RendererLib.windowWidth} catch {case e:Exception => 800})/50) else 0,
                         init_N_y:Int            = if(property("field.h_y", 0) == 0) property("field.N_y", (try {RendererLib.windowHeight} catch {case e:Exception => 600})/50) else 0,
                         solid_edges:Boolean     = property("field.solid_edges", true)) = {
    new CoordTracer[T](field_from_x,field_to_x,field_from_y,field_to_y,init_h_x,init_h_y,init_N_x,init_N_y,solid_edges)
  }
}