package scala.build.options

import dependency.*

import scala.build.Positioned

final case class ClassPathOptions(
  extraRepositories: Seq[String] = Nil,
  extraClassPath: Seq[os.Path] = Nil,
  extraCompileOnlyJars: Seq[os.Path] = Nil,
  extraSourceJars: Seq[os.Path] = Nil,
  fetchSources: Option[Boolean] = None,
  extraDependencies: ShadowingSeq[Positioned[AnyDependency]] = ShadowingSeq.empty,
  extraCompileOnlyDependencies: ShadowingSeq[Positioned[AnyDependency]] = ShadowingSeq.empty,
  resourcesDir: Seq[os.Path] = Nil,
  resourcesVirtualDir: Seq[os.SubPath] = Nil,
  scalafixDependencies: ShadowingSeq[Positioned[AnyDependency]] = ShadowingSeq.empty
) {
  def allExtraDependencies: ShadowingSeq[Positioned[AnyDependency]] =
    extraDependencies ++ extraCompileOnlyDependencies.toSeq
}

object ClassPathOptions {
  implicit val hasHashData: HasHashData[ClassPathOptions] = HasHashData.derive
  implicit val monoid: ConfigMonoid[ClassPathOptions]     = ConfigMonoid.derive
}
