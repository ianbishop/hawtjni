/* NOTE this file is autogenerated by Scalate : see http://scalate.fusesource.org/ */

import _root_.scala.collection.JavaConversions._
import _root_.org.fusesource.scalate.support.TemplateConversions._
import _root_.org.fusesource.scalate.util.Measurements._

object $_scalate_$_scalate_tmp_6726790854910587663_ssp {
  def $_scalate_$render($_scalate_$_context: _root_.org.fusesource.scalate.RenderContext): Unit = {
    ;{
      implicit val context: _root_.org.fusesource.scalate.support.DummyRenderContext = $_scalate_$_context.attribute("context")
      import context._
      ;{
        val response: _root_.org.fusesource.scalate.support.DummyResponse = $_scalate_$_context.attributeOrElse("response", new _root_.org.fusesource.scalate.support.DummyResponse())
        
    // common imports go here
    import _root_.Website._;
    
        
        $_scalate_$_context << ( "# Copyright (C) 2009-2011 the original author or authors.\n# See the notice.md file distributed with this work for additional\n# information regarding copyright ownership.\n# \n# Licensed under the Apache License, Version 2.0 (the \"License\");\n# you may not use this file except in compliance with the License.\n# You may obtain a copy of the License at\n# \n#     http://www.apache.org/licenses/LICENSE-2.0\n# \n# Unless required by applicable law or agreed to in writing, software\n# distributed under the License is distributed on an \"AS IS\" BASIS,\n# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n# See the License for the specific language governing permissions and\n# limitations under the License.\n\nblog_post: true\ntitle: HawtJNI 1.0\nauthor: Hiram Chirino\nauthor_url: http://hiramchirino.com\ncreated_at: 2010-02-24 12:06:40 -0000\n" );
      }
    }
  }
}


class $_scalate_$_scalate_tmp_6726790854910587663_ssp extends _root_.org.fusesource.scalate.Template {
  def render(context: _root_.org.fusesource.scalate.RenderContext): Unit = $_scalate_$_scalate_tmp_6726790854910587663_ssp.$_scalate_$render(context)
}
