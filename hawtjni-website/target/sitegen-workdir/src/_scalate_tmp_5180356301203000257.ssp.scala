/* NOTE this file is autogenerated by Scalate : see http://scalate.fusesource.org/ */

import _root_.scala.collection.JavaConversions._
import _root_.org.fusesource.scalate.support.TemplateConversions._
import _root_.org.fusesource.scalate.util.Measurements._

object $_scalate_$_scalate_tmp_5180356301203000257_ssp {
  def $_scalate_$render($_scalate_$_context: _root_.org.fusesource.scalate.RenderContext): Unit = {
    ;{
      implicit val context: _root_.org.fusesource.scalate.support.DummyRenderContext = $_scalate_$_context.attribute("context")
      import context._
      ;{
        val response: _root_.org.fusesource.scalate.support.DummyResponse = $_scalate_$_context.attributeOrElse("response", new _root_.org.fusesource.scalate.support.DummyResponse())
        
    // common imports go here
    import _root_.Website._;
    
        
        $_scalate_$_context << ( "\n* [" );
        $_scalate_$_context <<< (         project_name
 );
        $_scalate_$_context << ( " " );
        $_scalate_$_context <<< (         project_version
 );
        $_scalate_$_context << ( "](" );
        $_scalate_$_context <<< (         uri("/")
 );
        $_scalate_$_context << ( ") \n* [Docs](" );
        $_scalate_$_context <<< (         uri("/documentation/developer-guide.html")
 );
        $_scalate_$_context << ( ")\n* [Support](" );
        $_scalate_$_context <<< (         uri("/community/index.html")
 );
        $_scalate_$_context << ( ")\n<!-- * [Download](" );
        $_scalate_$_context <<< (         uri("/download.html")
 );
        $_scalate_$_context << ( ")  -->\n\n" );
      }
    }
  }
}


class $_scalate_$_scalate_tmp_5180356301203000257_ssp extends _root_.org.fusesource.scalate.Template {
  def render(context: _root_.org.fusesource.scalate.RenderContext): Unit = $_scalate_$_scalate_tmp_5180356301203000257_ssp.$_scalate_$render(context)
}
