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
package com.excilys.ebi.gatling.jenkins.targetenvgraphs;

public enum Environment {
    FOXTROT("foxtrot", true),
    STAGE("stage", true),
    PROD("prod", true),
    BETA("beta", true),
    TPLNP("lnp", false);

    public final String name;
    public final boolean isSflyEnv;

    private Environment(String name, boolean isSflyEnv) {
        this.name = name;
        this.isSflyEnv = isSflyEnv;
    }
}
