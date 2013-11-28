/**
 * Copyright (C) 2009-2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.fusesource.scalate.RenderContext

package

/**
 * <p>
 * </p>
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
object Website {

  val project_name= "HawtJNI"
  val project_slogan= "Making JNI easy and fast."
  val project_id= "hawtjni"
  val project_jira_key= "HAWTJNI"
  val project_issue_url= "https://github.com/fusesource/hawtjni/issues"
  val project_forums_url= "http://groups.google.com/group/hawtjni"
  val project_wiki_url= "https://github.com/fusesource/hawtjni/wiki"
  val project_logo= "/images/project-logo.png"
  val project_version= "1.8"
  val project_snapshot_version= "1.9-SNAPSHOT"
  val project_versions = List(
        project_version,
        "1.7",
        "1.6",
        "1.5",
        "1.4",
        "1.3",
        "1.2",
        "1.1",
        "1.0"
        )  

  val project_keywords= "jni,java,jna,c,objective-c,c++,c#"

  // -------------------------------------------------------------------
  val github_page= "http://github.com/fusesource/hawtjni"
  val git_user_url= "git://github.com/fusesource/hawtjni.git"
  val git_commiter_url= "git@github.com:fusesources/hawtjni.git"

  val project_maven_groupId= "org.fusesource.hawtjni"
  val project_maven_artifactId= "hawtjni-runtime"

  val website_base_url= "http://hawtjni.fusesource.org"
}