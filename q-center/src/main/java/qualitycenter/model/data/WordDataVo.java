package qualitycenter.model.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import com.ndata.quality.model.std.StdWordVo;

@Data
public class WordDataVo {
    private String wordNm;
    private List<StdWordVo> wordLst = new ArrayList<StdWordVo>();
}