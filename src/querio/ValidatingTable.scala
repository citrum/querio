package querio


trait ValidatingTable[MTR <: MutableTableRecord[_ <: TableRecord]] {self: AnyTable =>
  def _patchMTR(mtr: MTR, fields: Map[String, String]) {
    val table = self.asInstanceOf[Table[_, MTR]]
    for ((name, value) <- fields) {
      val field = table._fields.find(_.name == name).getOrElse(sys.error("Invalid field name: '" + name + "'"))
      try field.setFromString(mtr, value)
      catch {
        case e: Exception => throw new RuntimeException("Field: " + field.name + "\n" + e.toString, e)
      }
    }
  }

  def _newPatchedMTR(fields: Map[String, String]): MTR = {
    val mtr = _newMutableRecord.asInstanceOf[MTR]
    _patchMTR(mtr, fields)
    mtr
  }
}


class ValidatePatchReader(fields: Map[String, String]) {

  def intField(name: String, setter: Int => Unit) =
    fields.get(name).foreach(s => if (s == null) setter(0) else setter(s.toInt))
  def optionIntField(name: String, setter: Option[Int] => Unit) =
    fields.get(name).foreach(s => if (s == null) setter(None) else setter(Some(s.toInt)))
  def longField(name: String, setter: Long => Unit) =
    fields.get(name).foreach(s => if (s == null) setter(0L) else setter(s.toLong))
  def optionLongField(name: String, setter: Option[Long] => Unit) =
    fields.get(name).foreach(s => if (s == null) setter(None) else setter(Some(s.toLong)))

  def stringField(name: String, setter: String => Unit) =
    fields.get(name).foreach {s =>
      if (s == null || s.isEmpty) sys.error("Null or empty string unsupported for field " + name)
      else setter(s)
    }
  def optionStringField(name: String, setter: Option[String] => Unit) =
    fields.get(name).foreach(s => if (s == null || s.isEmpty) setter(None) else setter(Some(s)))

  def enumField[E <: ScalaDbEnumCls[E]](name: String, setter: E => Unit, enum: ScalaDbEnum[E]) =
    stringField(name, s => setter(enum.getValue(s).getOrElse(sys.error("Invalid enum value '" + s + "' for " + enum.getClass.getSimpleName))))
  def optionEnumField[E <: ScalaDbEnumCls[E]](name: String, setter: Option[E] => Unit, enum: ScalaDbEnum[E]) =
    stringField(name, s => setter(enum.getValue(s)))
}
