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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashSet;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class TextFileViewer implements HdfsFileViewer {

    private HashSet<String> acceptedSuffix = new HashSet<String>();
    
    public TextFileViewer() {
        acceptedSuffix.add(".txt");
        acceptedSuffix.add(".csv");
        acceptedSuffix.add(".props");
        acceptedSuffix.add(".xml");
        acceptedSuffix.add(".html");
        acceptedSuffix.add(".json");
    }
    
    public boolean canReadFile(FileSystem fs, Path path) {
        for(String suffix: acceptedSuffix) {
            if (path.toString().endsWith(suffix)) {
                return true;
            }
        }
        
        return false;
    }

    public void displayFile(FileSystem fs, Path path, PrintWriter output, int startLine, int endLine)
    throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(path)));
        for(int i = 1; i < startLine; i++)
            reader.readLine();
        for(int i = startLine; i < endLine; i++) {
            String line = reader.readLine();
            if(line == null)
                break;
            output.write(line);
            output.write("\n");
        }
        output.flush();
        reader.close();
    }
}