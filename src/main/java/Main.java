import com.marklogic.util.Consts;
import com.marklogic.util.UriKeyFileProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * Created with IntelliJ IDEA.
 * User: alexb
 * Date: 02/05/15
 * Time: 12:57
 * To change this template use File | Settings | File Templates.
 */
public class Main {


    public static void main(String[] args) {

        Logger LOG = LoggerFactory.getLogger(Main.class);
        UriKeyFileProcessor ukfp = new UriKeyFileProcessor();

        LOG.info(MessageFormat.format("Comparing Directories: {0} [earliest] and {1} [latest]", Consts.EARLIER_BACKUP, Consts.LATER_BACKUP));

        ukfp.examineFilesAtPath(Consts.EARLIER_BACKUP, true);
        ukfp.examineFilesAtPath(Consts.LATER_BACKUP, false);

        ukfp.report();
    }
}

