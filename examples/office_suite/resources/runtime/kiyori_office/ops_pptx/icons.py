"""随包原创线性图标：真实可编辑矢量路径，不以圆点冒充图标。"""

from ..protocol import OfficeError
from .layout import rgb, measure

# 24 单位坐标，路径均由本项目定义，无网络素材与字体图标依赖。
PATHS = {
    "check": [[(3,12),(9,18),(21,5)]],
    "arrow_right": [[(3,12),(21,12)],[(14,5),(21,12),(14,19)]],
    "chart": [[(3,3),(3,21),(22,21)],[(7,17),(7,11)],[(12,17),(12,7)],[(18,17),(18,3)]],
    "document": [[(5,2),(15,2),(20,7),(20,22),(5,22),(5,2)],[(15,2),(15,7),(20,7)],[(8,11),(17,11)],[(8,15),(17,15)],[(8,19),(14,19)]],
    "download": [[(12,2),(12,16)],[(6,10),(12,16),(18,10)],[(3,17),(3,22),(21,22),(21,17)]],
    "play": [[(6,3),(21,12),(6,21),(6,3)]],
    "code": [[(8,5),(2,12),(8,19)],[(16,5),(22,12),(16,19)],[(14,3),(10,21)]],
    "search": [[(7,3),(12,3),(16,7),(16,12),(12,16),(7,16),(3,12),(3,7),(7,3)],[(15,15),(22,22)]],
}


def add_icon(slide, box, spec):
    from pptx.util import Pt
    name=spec.get("icon")
    if name not in PATHS:
        raise OfficeError("E_INPUT_SCHEMA", "icon 支持：%s" % "/".join(PATHS))
    left,top,width,height=box
    paths=[]
    for path in PATHS[name]:
        builder=slide.shapes.build_freeform(*path[0],scale=(width/24,height/24))
        builder.add_line_segments(path[1:],close=False)
        shape=builder.convert_to_shape(left,top)
        shape.fill.background()
        shape.shadow.inherit = False
        shape.line.color.rgb=rgb(spec.get("color_rgb","334155"))
        shape.line.width=Pt(measure(spec.get("line_width_pt",1.5),"line_width_pt",0.1,20))
        paths.append(shape)
    return slide.shapes.add_group_shape(paths)
