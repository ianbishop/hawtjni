/* NOTE this file is autogenerated by Scalate : see http://scalate.fusesource.org/ */

import _root_.scala.collection.JavaConversions._
import _root_.org.fusesource.scalate.support.TemplateConversions._
import _root_.org.fusesource.scalate.util.Measurements._

object $_scalate_$_scalate_tmp_1201876933942361543_ssp {
  def $_scalate_$render($_scalate_$_context: _root_.org.fusesource.scalate.RenderContext): Unit = {
    ;{
      implicit val context: _root_.org.fusesource.scalate.support.DummyRenderContext = $_scalate_$_context.attribute("context")
      import context._
      ;{
        val response: _root_.org.fusesource.scalate.support.DummyResponse = $_scalate_$_context.attributeOrElse("response", new _root_.org.fusesource.scalate.support.DummyResponse())
        
    // common imports go here
    import _root_.Website._;
    
        
        $_scalate_$_context << ( "\nThe [" );
        $_scalate_$_context <<< (         project_name
 );
        $_scalate_$_context << ( "](" );
        $_scalate_$_context <<< (         website_base_url
 );
        $_scalate_$_context << ( ") project is pleased to announce\nthe availability of HawtJNI 1.6. HawtJNI is a code generator that produces\nthe JNI code needed to implement java native methods. It is based on the\n[jnigen](http://www.eclipse.org/swt/jnigen.php) code generator that is part\nof the SWT Tools project which is used to generate all the JNI code which\npowers the eclipse platform.\n\nRelease Highlights:\n\n* Only include config.h if it's available.\n\nFurther information:\n\n* [Documentation](" );
        $_scalate_$_context <<< (         website_base_url
 );
        $_scalate_$_context << ( "/documentation/developer-guide.html)\n\n[Feedback](" );
        $_scalate_$_context <<< (         website_base_url
 );
        $_scalate_$_context << ( "/community/index.html) is always welcome! \n\n" );
      }
    }
  }
}


class $_scalate_$_scalate_tmp_1201876933942361543_ssp extends _root_.org.fusesource.scalate.Template {
  def render(context: _root_.org.fusesource.scalate.RenderContext): Unit = $_scalate_$_scalate_tmp_1201876933942361543_ssp.$_scalate_$render(context)
}
