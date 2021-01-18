/**********************************************************************************
 *
 * Copyright (c) 2015 The Sakai Foundation
 *
 * Original developers:
 *
 *   New York University
 *   Payten Giles
 *   Mark Triggs
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaiproject.content.googledrive;

import org.sakaiproject.entity.api.ResourceProperties;
import java.util.Collections;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    // List items added with properties.addPropertyToList() seem to get lost
    // when saving a new resource.  The call to
    // BaseContentService.addProperties uses
    // ResourceProperties.getProperty(name), which returns null for
    // non-strings.  Since our properties are stored as Vectors, they get
    // nulled too.
    //
    // So, for now we're doing our own serialization.
    public static void storeStringArray(ResourceProperties properties,
                                        String key,
                                        Collection<String> values) {
        if (values == null) {
            values = Collections.emptyList();
        }

        properties.addProperty(key + "_count", String.valueOf(values.size()));

        int count = 0;
        for (String value : values) {
            properties.addProperty(String.format("%s_%d", key, count), value);
        }
    }

    public static List<String> loadStringArray(ResourceProperties properties, String key) {
        String countStr = properties.getProperty(key + "_count");
        if (countStr == null) {
            countStr = "0";
        }

        int count = Integer.valueOf(countStr);

        List<String> result = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            result.add(properties.getProperty(String.format("%s_%d", key, i)));
        }

        return result;
    }

}
