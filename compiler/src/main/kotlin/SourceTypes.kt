package tachiyomix.compiler

import javax.lang.model.util.Elements

class SourceTypes(private val elements: Elements) {

  val dependencies = elements.getTypeElement("tachiyomi.source.Dependencies").asType()

  val deeplinksource = elements.getTypeElement("tachiyomi.source.DeepLinkSource").asType()

  val source = elements.getTypeElement("tachiyomi.source.Source").asType()

}
