"""原生转场与有限、明确的放映动画；不从静态预览推断播放正确。"""

from ..protocol import OfficeError
from .layout import measure
from .style import checked, boolean


def node(tag, **attrs):
    from pptx.oxml.xmlchemy import OxmlElement
    result = OxmlElement(tag)
    for key, value in attrs.items():
        result.set(key, str(value))
    return result


def replace_slide_child(slide, child, name):
    from pptx.oxml.ns import qn
    root = slide._element
    for old in list(root):
        if old.tag == qn("p:" + name):
            root.remove(old)
    if child is not None:
        # CT_Slide 顺序：cSld, clrMapOvr, transition, timing, extLst。
        before = {qn("p:extLst")}
        if name == "transition":
            before.add(qn("p:timing"))
        index = next((i for i, item in enumerate(root) if item.tag in before), len(root))
        root.insert(index, child)


def transition(slide, config):
    checked(config, {"effect", "speed", "direction", "advance_on_click", "advance_after_ms"}, "transition")
    effect = config.get("effect", "fade")
    if effect not in {"none", "fade", "push", "wipe", "split", "cover", "uncover"}:
        raise OfficeError("E_INPUT_SCHEMA", "transition.effect 支持 none/fade/push/wipe/split/cover/uncover")
    speed = config.get("speed", "med")
    if speed not in {"slow", "med", "fast"}:
        raise OfficeError("E_INPUT_SCHEMA", "transition.speed 支持 slow/med/fast")
    target = node("p:transition", spd=speed, advClick=int(boolean(config.get("advance_on_click", True), "advance_on_click")))
    if "advance_after_ms" in config:
        target.set("advTm", str(int(measure(config["advance_after_ms"], "advance_after_ms", 0, 3600000))))
    if effect != "none":
        child = node("p:" + effect)
        if "direction" in config:
            direction = config["direction"]
            if effect not in {"push", "wipe", "cover", "uncover"} or direction not in {"l", "r", "u", "d"}:
                raise OfficeError("E_INPUT_SCHEMA", "direction 仅用于 push/wipe/cover/uncover，取 l/r/u/d")
            child.set("dir", direction)
        target.append(child)
    replace_slide_child(slide, target, "transition")


def animations(slide, specs):
    if not isinstance(specs, list) or len(specs) > 100:
        raise OfficeError("E_INPUT_SCHEMA", "animations 必须是最多 100 项的数组；空数组清除动画")
    if not specs:
        replace_slide_child(slide, None, "timing")
        return
    from .edit import _find_shape
    timing = node("p:timing")
    timing_list = node("p:tnLst")
    timing.append(timing_list)
    root_par = node("p:par")
    timing_list.append(root_par)
    root = node("p:cTn", id=1, dur="indefinite", restart="never", nodeType="tmRoot")
    root_par.append(root)
    root_children = node("p:childTnLst")
    root.append(root_children)
    seq = node("p:seq", concurrent=1, nextAc="seek")
    root_children.append(seq)
    main = node("p:cTn", id=2, dur="indefinite", nodeType="mainSeq")
    seq.append(main)
    children = node("p:childTnLst")
    main.append(children)
    next_id = 3
    click_children = None
    previous_start = previous_end = 0
    builds = node("p:bldLst")

    def start_condition(target, delay):
        conditions = node("p:stCondLst")
        conditions.append(node("p:cond", delay=delay))
        target.append(conditions)

    def timed_group(parent, delay, **attributes):
        nonlocal next_id
        par = node("p:par")
        parent.append(par)
        time = node("p:cTn", id=next_id, fill="hold", **attributes)
        next_id += 1
        par.append(time)
        start_condition(time, delay)
        children = node("p:childTnLst")
        time.append(children)
        return children

    for group_index, spec in enumerate(specs):
        checked(spec, {"shape_name", "shape_index", "effect", "trigger", "duration_ms", "delay_ms", "direction", "exit"}, "animations[]")
        shape = _find_shape(slide, spec.get("shape_index"), spec.get("shape_name"))
        effect = spec.get("effect", "fade")
        trigger = spec.get("trigger", "on_click")
        if effect not in {"fade", "wipe", "appear"} or trigger not in {"on_click", "with_previous", "after_previous"}:
            raise OfficeError("E_INPUT_SCHEMA", "animation effect 支持 fade/wipe/appear；trigger 支持 on_click/with_previous/after_previous")
        duration = int(measure(spec.get("duration_ms", 500), "duration_ms", 1, 60000))
        delay = int(measure(spec.get("delay_ms", 0), "delay_ms", 0, 3600000))
        is_exit = boolean(spec.get("exit", False), "exit")
        if trigger == "on_click" or click_children is None:
            click_children = timed_group(children, "indefinite" if trigger == "on_click" else 0)
            previous_start = previous_end = 0
        start = delay + (previous_start if trigger == "with_previous" else previous_end if trigger == "after_previous" else 0)
        # 与 PowerPoint 原生序列一致：一个点击组下按相对时间调度各效果；
        # 不能把 after_previous 生成为另一个独立点击组而要求再点一次。
        group_children = timed_group(click_children, start)
        filter_name = "fade"
        if effect == "wipe":
            direction = spec.get("direction", "up")
            if direction not in {"up", "down", "left", "right"}:
                raise OfficeError("E_INPUT_SCHEMA", "wipe.direction 支持 up/down/left/right")
            filter_name = "wipe(%s)" % direction
        elif "direction" in spec:
            raise OfficeError("E_INPUT_SCHEMA", "direction 仅用于 wipe")
        subtype = {"up": 8, "down": 4, "left": 2, "right": 1}.get(spec.get("direction", "up"), 0) if effect == "wipe" else 0
        behavior_list = timed_group(group_children, 0, presetID={"appear": 1, "fade": 10, "wipe": 22}[effect],
                                   presetClass="exit" if is_exit else "entr", presetSubtype=subtype, grpId=group_index,
                                   nodeType={"on_click": "clickEffect", "with_previous": "withEffect", "after_previous": "afterEffect"}[trigger])
        duration = 1 if effect == "appear" else duration
        # 显示状态也是动画的一部分。缺少此节点时播放前可能提前露出元素。
        visibility = node("p:set")
        behavior_list.append(visibility)
        behavior = node("p:cBhvr")
        visibility.append(behavior)
        time = node("p:cTn", id=next_id, dur=1, fill="hold")
        next_id += 1
        start_condition(time, duration if is_exit else 0)
        behavior.append(time)
        target = node("p:tgtEl")
        target.append(node("p:spTgt", spid=shape.shape_id))
        behavior.append(target)
        attributes = node("p:attrNameLst")
        attribute = node("p:attrName")
        attribute.text = "style.visibility"
        attributes.append(attribute)
        behavior.append(attributes)
        to = node("p:to")
        to.append(node("p:strVal", val="hidden" if is_exit else "visible"))
        visibility.append(to)
        if effect != "appear":
            animation = node("p:animEffect", transition="out" if is_exit else "in", filter=filter_name)
            behavior_list.append(animation)
            behavior = node("p:cBhvr")
            animation.append(behavior)
            behavior.append(node("p:cTn", id=next_id, dur=duration))
            next_id += 1
            target = node("p:tgtEl")
            target.append(node("p:spTgt", spid=shape.shape_id))
            behavior.append(target)
        if shape.has_text_frame:
            builds.append(node("p:bldP", spid=shape.shape_id, grpId=group_index))
        previous_start, previous_end = start, start + duration
    for tag, event in (("prevCondLst", "onPrev"), ("nextCondLst", "onNext")):
        conditions = node("p:" + tag)
        condition = node("p:cond", evt=event, delay=0)
        target = node("p:tgtEl")
        target.append(node("p:sldTgt"))
        condition.append(target)
        conditions.append(condition)
        seq.append(conditions)
    if len(builds):
        timing.append(builds)
    replace_slide_child(slide, timing, "timing")
