/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.algebricks.api.data;

import java.io.PrintStream;

import edu.uci.ics.algebricks.api.exceptions.AlgebricksException;

public interface IPrinter {
    public void init() throws AlgebricksException;

    public void print(byte[] b, int s, int l, PrintStream ps) throws AlgebricksException;
}
