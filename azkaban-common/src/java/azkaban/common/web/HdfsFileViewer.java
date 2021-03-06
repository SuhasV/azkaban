/*
 * Copyright 2010 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.common.web;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public interface HdfsFileViewer {

    public boolean canReadFile(FileSystem fs, Path path);

    public void displayFile(FileSystem fs,
                            Path path,
                            PrintWriter output,
                            int startLine,
                            int endLine) throws IOException;

}