/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.jenkins.targetenvgraphs.envgraphs.graphite;


/**
 * Created by jagte on 11/21/16.
 */
public class GrafanaUrl{
    public GrafanaUrl(String rawUrl, String urlDisplayName)
    {
        this.rawUrl=rawUrl;
        this.urlDisplayName=urlDisplayName;
    }

    public String rawUrl;
    public String urlDisplayName;

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof GrafanaUrl)){
            return false;
        }
        return this.rawUrl.equals(((GrafanaUrl)other).rawUrl)
                && this.urlDisplayName.equals(((GrafanaUrl)other).urlDisplayName);
    }
}
