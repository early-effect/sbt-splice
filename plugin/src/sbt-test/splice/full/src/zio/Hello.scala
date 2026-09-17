import zio.*

object Hello extends ZIOAppDefault:
  def run = System.env("PATH").as(())
