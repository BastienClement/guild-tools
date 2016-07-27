package gtp3

object PayloadFlags {
	final val None = 0x00
	final val Compress = 0x01
	final val Utf8Data = 0x02
	final val JsonData = 0x04
	// Require Utf8Data
	final val PickledData = 0x10
	final val Ignore = 0x80
}
