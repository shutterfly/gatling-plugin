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
package io.gatling.jenkins.targetenvgraphs;

public enum Brand {
    SHUTTERFLY("sfly"),
    TINYPRINTS("tp");

    public final String name;

    private Brand(String name) {
        this.name = name;
    }

    public static Brand getBrandFromName(String brandName) {
        if(null != brandName && brandName.trim().length() > 0) {
            for(Brand brand: Brand.values()) {
                if(brandName.trim().equalsIgnoreCase(brand.name)) {
                    return brand;
                }
            }
        }
        return null;
    }
}
