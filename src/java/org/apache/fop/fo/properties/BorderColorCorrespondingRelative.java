/*
 *
 * Copyright 2004 The Apache Software Foundation.
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
 *
 * Created on 22/04/2004
 * $Id$
 */
package org.apache.fop.fo.properties;

import org.apache.fop.fo.FONode;
import org.apache.fop.fo.PropNames;
import org.apache.fop.fo.expr.PropertyException;

/**
 * @author pbw
 * @version $Revision$ $Name$
 */
public class BorderColorCorrespondingRelative extends BorderColorCorresponding {

    /** Array of absolute border color properties,
     * indexed by absolute edge constants */
    private static int[] absBorderColorProps = {
            PropNames.NO_PROPERTY
            ,PropNames.BORDER_TOP_COLOR
            ,PropNames.BORDER_BOTTOM_COLOR
            ,PropNames.BORDER_LEFT_COLOR
            ,PropNames.BORDER_RIGHT_COLOR
    };

    /**
     * Gets the absolute border color property corresponding to the given
     * relative edge
     * @param foNode the node on which the property is being defined
     * @param relativeEdge
     * @return the relative border color property index
     * @throws PropertyException
     */
    protected int getCorrespondingColorProperty(
            FONode foNode, int relativeEdge)
    throws PropertyException {
        int absEdge = WritingMode.getCorrespondingAbsoluteEdge(
                getWritingMode(foNode), relativeEdge);
        return absBorderColorProps[absEdge];
    }

    public boolean overridesCorresponding(FONode foNode) {
        return false;
    }

}
