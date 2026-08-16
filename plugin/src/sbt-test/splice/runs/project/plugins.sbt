sys.props.get("plugin.version") match {
  case Some(v) => addSbtPlugin("rocks.earlyeffect" % "sbt-splice" % v)
  case _       =>
    sys.error("""|The system property 'plugin.version' is not defined.
                 |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

libraryDependencies += "org.graalvm.polyglot" % "polyglot"        % "25.2.4"
libraryDependencies += "org.graalvm.js"       % "js-language"     % "25.2.4"
libraryDependencies += "org.graalvm.truffle"  % "truffle-runtime" % "25.2.4"
