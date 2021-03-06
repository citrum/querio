package querio
import java.sql.{PreparedStatement, ResultSet, Array => SqlArray}

import org.apache.commons.lang3.{ArrayUtils, StringUtils}
import querio.vendor.Vendor

import scala.reflect.ClassTag

// ------------------------------- ArrayEl, ArrayField -------------------------------

trait ArrayEl[T, V] extends El[Array[T], V] {self =>
  def elementRenderer: TypeRenderer[T]
  def elementParser: TypeParser[T]
  def elementDataType: String

  override def tRenderer(vendor: Vendor): TypeRenderer[Array[T]] = elementRenderer.toMkStringRendererArray(vendor.arrayMkString(elementDataType))
  //  override def vRenderer(vendor: Vendor): TypeRenderer[Array[T]] = tRenderer(vendor)

  def condition(op: String, value: Seq[T]): Condition = new Condition {
    override def renderCond(buf: SqlBuffer) {buf ++ self ++ op; elementRenderer.toMkStringRendererIterable(buf.vendor.arrayMkString(elementDataType)).render(value, self)(buf)}
  }
  def ==(value: Seq[T]): Condition = condition(" = ", value)
  def !=(value: Seq[T]): Condition = condition(" != ", value)
}

trait ArrayField[T, V] extends Field[Array[T], V] with ArrayEl[T, V] {
  /** ClassTag needed to create Array[T] in [[parser.parse()]] method */
  implicit def classTag: ClassTag[T]

  def objectsToArray(v: AnyRef): Array[T]
  def arrayToObjects(v: Array[T]): Array[_ <: AnyRef]
}

trait ArrayObjectField[T <: AnyRef, V] extends ArrayField[T, V] {
  override def objectsToArray(v: AnyRef): Array[T] = v.asInstanceOf[Array[T]]
  override def arrayToObjects(v: Array[T]): Array[_ <: AnyRef] = v
}


trait SimpleArrayField[T] extends ArrayField[T, Array[T]] {
  override def vRenderer(vendor: Vendor): TypeRenderer[Array[T]] = tRenderer(vendor)
  override def parser: TypeParser[Array[T]] = new ArrayTypeParser[T](elementParser)

  def getValue(rs: ResultSet, index: Int): Array[T] = {
    val array: SqlArray = rs.getArray(index)
    val javaArray = array.getArray
    array.free()
    objectsToArray(javaArray)
  }
  def setValue(st: PreparedStatement, index: Int, value: Array[T]): Unit = {
    checkNotNull(value)
    st.setArray(index, st.getConnection.createArrayOf(elementDataType, arrayToObjects(value).asInstanceOf[Array[AnyRef]]))
  }

  override def valueEquals(a: Array[T], b: Array[T]): Boolean = a.sameElements(b)
}

trait OptionArrayField[T] extends ArrayField[T, Option[Array[T]]] {
  override def vRenderer(vendor: Vendor): TypeRenderer[Option[Array[T]]] = tRenderer(vendor).toOptionRenderer
  override def parser: TypeParser[Option[Array[T]]] = new ArrayTypeParser[T](elementParser).toOptionParser

  def getValue(rs: ResultSet, index: Int): Option[Array[T]] = {
    val array: SqlArray = rs.getArray(index)
    if (rs.wasNull()) {
      None
    } else {
      val javaArray = array.getArray
      array.free()
      Some(objectsToArray(javaArray))
    }
  }
  def setValue(st: PreparedStatement, index: Int, value: Option[Array[T]]): Unit = {
    value.foreach {v =>
      checkNotNull(v)
      st.setArray(index, st.getConnection.createArrayOf(elementDataType, arrayToObjects(v).asInstanceOf[Array[AnyRef]]))
    }
  }

  override def valueEquals(a: Option[Array[T]], b: Option[Array[T]]): Boolean = {
    if (a.isDefined) {
      if (b.isDefined) a.get sameElements b.get
      else false
    } else {
      b.isEmpty
    }
  }
}

// Empty set treats as null value in database
trait SetArrayField[T] extends ArrayField[T, Set[T]] {
  override def vRenderer(vendor: Vendor): TypeRenderer[Set[T]] = elementRenderer.toMkStringRendererIterable(vendor.arrayMkString(elementDataType))
  override def parser: TypeParser[Set[T]] = new SetTypeParser[T](elementParser)

  override def getValue(rs: ResultSet, index: Int): Set[T] = {
    val array: SqlArray = rs.getArray(index)
    if (rs.wasNull()) {
      Set.empty
    } else {
      val javaArray = array.getArray
      array.free()
      objectsToArray(javaArray).to
    }
  }

  override def setValue(st: PreparedStatement, index: Int, value: Set[T]): Unit = {
    checkNotNull(value)
    if (value.nonEmpty) {
      val array: Array[AnyRef] = new Array(value.size)
      var i = 0
      value.foreach {item =>
        array(i) = item.asInstanceOf[AnyRef]
        i += 1
      }
      st.setArray(index, st.getConnection.createArrayOf(elementDataType, array))
    }
  }
}

// ------------------------------- Boolean[] -------------------------------

trait ArrayBooleanField[V] extends ArrayField[Boolean, V] {self =>
  override implicit def classTag: ClassTag[Boolean] = ClassTag.Boolean
  override def elementRenderer: TypeRenderer[Boolean] = BooleanRenderer
  override def elementParser: TypeParser[Boolean] = BooleanParser
  override def objectsToArray(v: AnyRef): Array[Boolean] = ArrayUtils.toPrimitive(v.asInstanceOf[Array[java.lang.Boolean]])
  override def arrayToObjects(v: Array[Boolean]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Boolean], Array[Boolean]] = new ArrayBooleanField[Array[Boolean]] with SimpleArrayField[Boolean] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Short[] -------------------------------

trait ArrayShortField[V] extends ArrayField[Short, V] {self =>
  override implicit def classTag: ClassTag[Short] = ClassTag.Short
  override def elementRenderer: TypeRenderer[Short] = ShortRenderer
  override def elementParser: TypeParser[Short] = ShortParser
  override def objectsToArray(v: AnyRef): Array[Short] = v match {
    case null => null
    case arr: Array[java.lang.Short] => ArrayUtils.toPrimitive(arr)
    case arr: Array[java.lang.Number] =>
      if (arr.length == 0) ArrayUtils.EMPTY_SHORT_ARRAY
      else {
        val result = new Array[Short](arr.length)
        var i = 0
        while (i < result.length) {
          result(i) = arr(i).shortValue()
          i += 1
        }
        result
      }
  }
  override def arrayToObjects(v: Array[Short]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Short], Array[Short]] = new ArrayShortField[Array[Short]] with SimpleArrayField[Short] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Int[] -------------------------------

trait ArrayIntField[V] extends ArrayField[Int, V] {self =>
  override implicit def classTag: ClassTag[Int] = ClassTag.Int
  override def elementRenderer: TypeRenderer[Int] = IntRenderer
  override def elementParser: TypeParser[Int] = IntParser
  override def objectsToArray(v: AnyRef): Array[Int] = ArrayUtils.toPrimitive(v.asInstanceOf[Array[java.lang.Integer]])
  override def arrayToObjects(v: Array[Int]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Int], Array[Int]] = new ArrayIntField[Array[Int]] with SimpleArrayField[Int] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Long[] -------------------------------

trait ArrayLongField[V] extends ArrayField[Long, V] {self =>
  override implicit def classTag: ClassTag[Long] = ClassTag.Long
  override def elementRenderer: TypeRenderer[Long] = LongRenderer
  override def elementParser: TypeParser[Long] = LongParser
  override def objectsToArray(v: AnyRef): Array[Long] = ArrayUtils.toPrimitive(v.asInstanceOf[Array[java.lang.Long]])
  override def arrayToObjects(v: Array[Long]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Long], Array[Long]] = new ArrayLongField[Array[Long]] with SimpleArrayField[Long] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- String[] -------------------------------

trait ArrayStringField[V] extends ArrayObjectField[String, V] {self =>
  override implicit def classTag: ClassTag[String] = ClassTag(classOf[String])
  override def elementRenderer: TypeRenderer[String] = StringRenderer
  override def elementParser: TypeParser[String] = StringParser
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[String], Array[String]] = new ArrayStringField[Array[String]] with SimpleArrayField[String] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Float[] -------------------------------

trait ArrayFloatField[V] extends ArrayField[Float, V] {self =>
  override implicit def classTag: ClassTag[Float] = ClassTag.Float
  override def elementRenderer: TypeRenderer[Float] = FloatRenderer
  override def elementParser: TypeParser[Float] = FloatParser
  override def objectsToArray(v: AnyRef): Array[Float] = ArrayUtils.toPrimitive(v.asInstanceOf[Array[java.lang.Float]])
  override def arrayToObjects(v: Array[Float]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Float], Array[Float]] = new ArrayFloatField[Array[Float]] with SimpleArrayField[Float] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Double[] -------------------------------

trait ArrayDoubleField[V] extends ArrayField[Double, V] {self =>
  override implicit def classTag: ClassTag[Double] = ClassTag.Double
  override def elementRenderer: TypeRenderer[Double] = DoubleRenderer
  override def elementParser: TypeParser[Double] = DoubleParser
  override def objectsToArray(v: AnyRef): Array[Double] = ArrayUtils.toPrimitive(v.asInstanceOf[Array[java.lang.Double]])
  override def arrayToObjects(v: Array[Double]): Array[_ <: AnyRef] = ArrayUtils.toObject(v)
  override def newExpression(r: (SqlBuffer) => Unit): El[Array[Double], Array[Double]] = new ArrayDoubleField[Array[Double]] with SimpleArrayField[Double] {
    override def elementDataType: String = self.elementDataType
    override def render(implicit buf: SqlBuffer): Unit = r(buf)
  }
}

// ------------------------------- Table fields -------------------------------

trait ArrayTableFields[TR <: TableRecord, MTR <: MutableTableRecord[TR]] {self: Table[TR, MTR] =>
  abstract class SimpleArrayTableField[T](tfd: TFD[Array[T]]) extends Field[Array[T], Array[T]](tfd) with querio.SimpleArrayField[T] {field =>
    def :=(value: Array[T]): FieldSetClause = new FieldSetClause(this) {
      override def renderValue(implicit buf: SqlBuffer): Unit = renderT(value)
    }
    def :=(value: Iterable[T]): FieldSetClause = new FieldSetClause(this) {
      override def renderValue(implicit buf: SqlBuffer): Unit = elementRenderer.toMkStringRendererIterable(buf.vendor.arrayMkString(elementDataType)).render(value, field)
    }
  }

  abstract class OptionArrayTableField[T](tfd: TFD[Option[Array[T]]]) extends Field[Array[T], Option[Array[T]]](tfd) with querio.OptionArrayField[T] {field =>
    def :=(value: Iterable[T]): FieldSetClause = new FieldSetClause(this) {
      override def renderValue(implicit buf: SqlBuffer): Unit = elementRenderer.toMkStringRendererIterable(buf.vendor.arrayMkString(elementDataType)).render(value, field)
    }
    def :=(value: Option[Array[T]]): FieldSetClause = new FieldSetClause(this) {
      override def renderValue(implicit buf: SqlBuffer): Unit = renderV(value)
    }
  }

  abstract class SetArrayTableField[T](tfd: TFD[Set[T]]) extends Field[Array[T], Set[T]](tfd) with querio.SetArrayField[T] {field =>
    def :=(value: Iterable[T]): FieldSetClause = new FieldSetClause(this) {
      override def renderValue(implicit buf: SqlBuffer): Unit = elementRenderer.toMkStringRendererIterable(buf.vendor.arrayMkString(elementDataType)).render(value, field)
    }
    def :=(value: Set[T]): FieldSetClause = this := (value: Iterable[T])
  }

  class ArrayBoolean_TF(val elementDataType: String)(tfd: TFD[Array[Boolean]]) extends SimpleArrayTableField[Boolean](tfd) with ArrayBooleanField[Array[Boolean]]
  class OptionArrayBoolean_TF(val elementDataType: String)(tfd: TFD[Option[Array[Boolean]]]) extends OptionArrayTableField[Boolean](tfd) with ArrayBooleanField[Option[Array[Boolean]]]

  class ArrayShort_TF(val elementDataType: String)(tfd: TFD[Array[Short]]) extends SimpleArrayTableField[Short](tfd) with ArrayShortField[Array[Short]]
  class OptionArrayShort_TF(val elementDataType: String)(tfd: TFD[Option[Array[Short]]]) extends OptionArrayTableField[Short](tfd) with ArrayShortField[Option[Array[Short]]]
  class SetArrayShort_TF(val elementDataType: String)(tfd: TFD[Set[Short]]) extends SetArrayTableField[Short](tfd) with ArrayShortField[Set[Short]]

  class ArrayInt_TF(val elementDataType: String)(tfd: TFD[Array[Int]]) extends SimpleArrayTableField[Int](tfd) with ArrayIntField[Array[Int]]
  class OptionArrayInt_TF(val elementDataType: String)(tfd: TFD[Option[Array[Int]]]) extends OptionArrayTableField[Int](tfd) with ArrayIntField[Option[Array[Int]]]
  class SetArrayInt_TF(val elementDataType: String)(tfd: TFD[Set[Int]]) extends SetArrayTableField[Int](tfd) with ArrayIntField[Set[Int]]

  class ArrayLong_TF(val elementDataType: String)(tfd: TFD[Array[Long]]) extends SimpleArrayTableField[Long](tfd) with ArrayLongField[Array[Long]]
  class OptionArrayLong_TF(val elementDataType: String)(tfd: TFD[Option[Array[Long]]]) extends OptionArrayTableField[Long](tfd) with ArrayLongField[Option[Array[Long]]]
  class SetArrayLong_TF(val elementDataType: String)(tfd: TFD[Set[Long]]) extends SetArrayTableField[Long](tfd) with ArrayLongField[Set[Long]]

  class ArrayString_TF(val elementDataType: String)(tfd: TFD[Array[String]]) extends SimpleArrayTableField[String](tfd) with ArrayStringField[Array[String]]
  class OptionArrayString_TF(val elementDataType: String)(tfd: TFD[Option[Array[String]]]) extends OptionArrayTableField[String](tfd) with ArrayStringField[Option[Array[String]]]
  class SetArrayString_TF(val elementDataType: String)(tfd: TFD[Set[String]]) extends SetArrayTableField[String](tfd) with ArrayStringField[Set[String]]

  class ArrayFloat_TF(val elementDataType: String)(tfd: TFD[Array[Float]]) extends SimpleArrayTableField[Float](tfd) with ArrayFloatField[Array[Float]]
  class OptionArrayFloat_TF(val elementDataType: String)(tfd: TFD[Option[Array[Float]]]) extends OptionArrayTableField[Float](tfd) with ArrayFloatField[Option[Array[Float]]]

  class ArrayDouble_TF(val elementDataType: String)(tfd: TFD[Array[Double]]) extends SimpleArrayTableField[Double](tfd) with ArrayDoubleField[Array[Double]]
  class OptionArrayDouble_TF(val elementDataType: String)(tfd: TFD[Option[Array[Double]]]) extends OptionArrayTableField[Double](tfd) with ArrayDoubleField[Option[Array[Double]]]
}

// ------------------------------- Utility classes -------------------------------

class ArrayTypeParser[T](elementParser: TypeParser[T])(implicit ct: ClassTag[T]) extends TypeParser[Array[T]] {
  override def parse(s: String): Array[T] = StringUtils.split(s, ',').map(s => elementParser.parse(s.trim))
}

class SetTypeParser[T](elementParser: TypeParser[T])(implicit ct: ClassTag[T]) extends TypeParser[Set[T]] {
  override def parse(s: String): Set[T] = StringUtils.split(s, ',').map(s => elementParser.parse(s.trim))(collection.breakOut)
}
