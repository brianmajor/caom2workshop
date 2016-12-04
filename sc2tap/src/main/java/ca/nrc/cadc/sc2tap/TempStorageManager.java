/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2016.                            (c) 2016.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 6 $
*
************************************************************************
*/

package ca.nrc.cadc.sc2tap;

import ca.nrc.cadc.dali.tables.TableWriter;
import ca.nrc.cadc.tap.ResultStore;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobInfo;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.server.RandomStringGenerator;
import ca.nrc.cadc.uws.web.InlineContentException;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.log4j.Logger;

/**
 * Basic temporary storage implementation for a tap service.
 * 
 * @author pdowler
 */
public class TempStorageManager implements ResultStore, InlineContentHandler
{
    private static final Logger log = Logger.getLogger(TempStorageManager.class);
 
    private static final String CONFIG = TempStorageManager.class.getSimpleName() + ".properties";
    private static final String BASE_DIR_KEY = TempStorageManager.class.getName() + ".baseStorageDir";
    private static final String BASE_URL_KEY = TempStorageManager.class.getName() + ".baseURL";
    
    private Job job;
    private String contentType;
    private String filename;
    
    private String baseDir;
    private String baseURL;
    
    public TempStorageManager() 
    { 
        try
        {
            URL url = TempStorageManager.class.getClassLoader().getResource(CONFIG);
            log.debug("read: " + url.toExternalForm());
            Properties props = new Properties();
            props.load(url.openStream());
            for (String s : props.stringPropertyNames())
                log.debug("props: " + s + "=" + props.getProperty(s));
            this.baseDir = props.getProperty(BASE_DIR_KEY);
            this.baseURL = props.getProperty(BASE_URL_KEY);
        }
        catch(Exception ex)
        {
            log.error("CONFIG: failed to load/read config from TempStorageManager.properties", ex);
            throw new RuntimeException("CONFIG: failed to load/read config from TempStorageManager.properties", ex);
        }
        if (baseDir == null || baseURL == null)
        {
            log.error("CONFIG: incomplete: baseDir=" + baseDir +"  baseURL=" + baseURL);
            throw new RuntimeException("CONFIG incomplete: baseDir=" + baseDir +" baseURL=" + baseURL);
        }
    }
    
    // used by TempStorageGetAction
    File getStoredFile(String filename)
    {
        return new File(baseDir + "/" + filename);
    }

    // cadc-tap-server ResultStore implementation
    public URL put(ResultSet rs, TableWriter<ResultSet> writer) 
        throws IOException
    {
        return put(rs, writer, null);
    }
    public URL put(ResultSet rs, TableWriter<ResultSet> writer, Integer maxRows) 
        throws IOException
    {
        Long num = null;
        if (maxRows != null)
            num = new Long(maxRows);
        
        // TODO: get requested content-type from job and store it with file
        // so that TempStorageAction can set content-type header correctly
        File dest = getDestFile(filename);
        URL ret = getURL(filename);
        FileOutputStream ostream = null;
        try
        {
            ostream = new FileOutputStream(dest);
            writer.write(rs, ostream, num);
        }
        finally
        {
            if (ostream != null)
                ostream.close();
        }
        return ret;
    }

    public URL put(Throwable t, TableWriter writer) throws IOException
    {
        // TODO: get requested content-type from job and store it with file
        // so that TempStorageAction can set content-type header correctly
        File dest = getDestFile(filename);
        URL ret = getURL(filename);
        FileOutputStream ostream = null;
        try
        {
            ostream = new FileOutputStream(dest);
            writer.write(t, ostream);
        }
        finally
        {
            if (ostream != null)
                ostream.close();
        }
        return ret;
    }

    public void setJob(Job job)
    {
        this.job = job;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }
    
    private File getDestFile(String filename)
    {
        File dir = new File(baseDir);
        if (!dir.exists())
            throw new RuntimeException(BASE_DIR_KEY + "=" + baseDir + " does not exist");
        if (!dir.isDirectory())
            throw new RuntimeException(BASE_DIR_KEY + "=" + baseDir + " is not a directory");
        if (!dir.canWrite())
            throw new RuntimeException(BASE_DIR_KEY + "=" + baseDir + " is not writable");
        
        return new File(dir, filename);
    }
    
    private URL getURL(String filename)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(baseURL);
        
        if ( !baseURL.endsWith("/") )
            sb.append("/");
        
        sb.append(filename);
        String s = sb.toString();
        try
        {
            return new URL(s);
        }
        catch(MalformedURLException ex)
        {
            throw new RuntimeException("failed to create URL from " + s, ex);
        }
    }
    
    // cadc-uws-server InlineContentHandler implementation
    private List<Parameter> params;
    private Map<String, URL> map = new TreeMap<>();
    
    @Override
    public void setParameterList(List<Parameter> params)
    {
        this.params = params;
    }

    @Override
    public List<Parameter> getParameterList()
    {
        if (params == null)
            params = new ArrayList<Parameter>();
        
        // rewrite an UPLOAD param values from param:inline-name
        // to http urkl in temp storage
        for (Parameter parameter : params)
        {
            if (parameter.getName().equals("UPLOAD"))
            {
                String upload = parameter.getValue();
                if (upload == null || upload.trim().isEmpty())
                    break;

                StringBuilder sb = new StringBuilder();
                String[] tables = upload.split(";");
                for (String table : tables)
                {
                    if (table.isEmpty())
                    {
                        sb.append(";");
                        continue;
                    }
                    String[] nameURI = table.split(",");
                    if (nameURI.length == 1)
                    {
                        sb.append(table);
                        sb.append(";");
                        continue;
                    }
                    String[] paramURI = nameURI[1].split(":");
                    if(paramURI.length == 2 && map.containsKey(paramURI[1]))
                    {
                        sb.append(nameURI[0]);
                        sb.append(",");
                        URL url = map.get(paramURI[1]);
                        sb.append(url.toString());
                        sb.append(";");
                    }
                    else
                    {
                        sb.append(table);
                        sb.append(";");
                    }
                }
                if (sb.length() > 0)
                {
                    if (sb.charAt(sb.length() - 1) == ';')
                        sb.deleteCharAt(sb.length() - 1);
                    parameter.setValue(sb.toString());
                }
            }
        }
        return params;
    }

    @Override
    public JobInfo getJobInfo()
    {
        return null;
    }

    @Override
    public URL accept(String name, String contentType, InputStream inputStream) 
        throws InlineContentException, IOException
    {
        // store the file in tmp storage
        log.debug("name: " + name);
        log.debug("Content-Type: " + contentType);
        if (inputStream == null)
            throw new IOException("InputStream cannot be null");

        String filename = name + "-" + getRandomString();
        
        File put = new File(baseDir + "/" + filename);
        URL retURL = new URL(baseURL + "/" + filename);
     
        log.debug("put: " + put);
        log.debug("contentType: " + contentType);
        
        FileOutputStream fos = new FileOutputStream(put);
        byte[] buf = new byte[16384];
        int num = inputStream.read(buf);
        while (num > 0)
        {
            fos.write(buf, 0, num);
            num = inputStream.read(buf);
        }
        fos.flush();
        fos.close();

        // Add the name and url to the map.
        map.put(name, retURL);
        log.debug("map.put[" + name + ", " + retURL + "]");
        return retURL;

    }
    
    private static String getRandomString()
    {
        return new RandomStringGenerator(16).getID();
    }
}
