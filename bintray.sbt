ThisBuild / publishTo := Some("bintray" at "https://api.bintray.com/maven/naftoligug/maven/cloud-logging-logback-scala")

sys.env.get("BINTRAYKEY").toSeq.map { key =>
  ThisBuild / credentials += Credentials("Bintray API Realm", "api.bintray.com", "naftoligug", key)
}
