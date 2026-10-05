// sbt-splice-zipx depends on an sbt-zipx commit snapshot until zipx's next release; drop this with that pin.
resolvers += Resolver.sonatypeCentralSnapshots

sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("rocks.earlyeffect" % "sbt-splice-zipx" % v)
  case _       =>
    sys.error("""|The system property 'plugin.version' is not defined.
                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}
