#!/usr/bin/env python3
"""Post-process the generated docx (Rev 2 plan):
1. Remove empty <w:pgNumType/> (cover section artifact).
2. Strip residual w:hyperlink elements inside the TOC field header paragraph
   (docx-js artifact that duplicated the first TOC entry in the Rev 1 build).
3. Remove consecutive duplicate TOC placeholder entries (same visible text and
   both containing PAGEREF fields).
4. Patch footer PAGE fields with explicit format switches: upperRoman section
   footer -> \\* ROMAN, decimal section footer -> \\* arabic (WPS ignores pgNumType).
"""
import re
import sys
import zipfile
from lxml import etree

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def qn(tag):
    return "{%s}%s" % (W, tag)


def para_text(p):
    return "".join(t.text or "" for t in p.iter(qn("t")))


def main(docx_path):
    zin = zipfile.ZipFile(docx_path)
    data = {n: zin.read(n) for n in zin.namelist()}
    zin.close()

    # 1) empty pgNumType removal
    data["word/document.xml"] = re.sub(rb"<w:pgNumType/>", b"", data["word/document.xml"])

    tree = etree.fromstring(data["word/document.xml"])

    # 2) strip residual hyperlinks in the TOC field header paragraph
    toc_begin_paras = []
    for p in tree.iter(qn("p")):
        has_begin = False
        has_toc = False
        for el in p.iter():
            if el.tag == qn("fldChar") and el.get(qn("fldCharType")) == "begin":
                has_begin = True
            if el.tag == qn("instrText") and "TOC" in (el.text or ""):
                has_toc = True
        if has_begin and has_toc:
            toc_begin_paras.append(p)
            for h in p.findall(qn("hyperlink")):
                p.remove(h)

    # 3) consecutive duplicate TOC placeholder entries
    pageref_paras = []
    for p in tree.iter(qn("p")):
        if any("PAGEREF" in (it.text or "") for it in p.iter(qn("instrText"))):
            pageref_paras.append(p)
    removed = 0
    for i in range(len(pageref_paras) - 1):
        p1, p2 = pageref_paras[i], pageref_paras[i + 1]
        t1, t2 = para_text(p1).strip(), para_text(p2).strip()
        if t1 and t1 == t2 and p2.getparent() is not None:
            p2.getparent().remove(p2)
            removed += 1

    data["word/document.xml"] = etree.tostring(
        tree, xml_declaration=True, encoding="UTF-8", standalone=True)

    # 4) footer PAGE format switches, mapped via sectPr pgNumType fmt
    rels = etree.fromstring(data["word/_rels/document.xml.rels"])
    relmap = {}
    for rel in rels:
        relmap[rel.get("Id")] = rel.get("Target")

    patched = []
    for sect in tree.iter(qn("sectPr")):
        pg = sect.find(qn("pgNumType"))
        if pg is None:
            continue
        fmt = pg.get(qn("fmt"))
        fr = sect.find(qn("footerReference"))
        if fr is None:
            continue
        rid = fr.get("{%s}id" % R)
        target = relmap.get(rid)
        if not target:
            continue
        fname = target if target.startswith("word/") else "word/" + target
        if fname not in data:
            continue
        switch = rb"PAGE \* ROMAN \* MERGEFORMAT" if fmt == "upperRoman" else rb"PAGE \* arabic \* MERGEFORMAT"
        new = re.sub(rb"(<w:instrText[^>]*>)\s*PAGE\s*(</w:instrText>)",
                     lambda m: m.group(1) + b" " + switch + b" " + m.group(2),
                     data[fname])
        if new != data[fname]:
            data[fname] = new
            patched.append((fname, fmt))

    with zipfile.ZipFile(docx_path, "w", zipfile.ZIP_DEFLATED) as zout:
        for name, blob in data.items():
            zout.writestr(name, blob)

    print("patched:", docx_path)
    print("  toc header paragraphs cleaned:", len(toc_begin_paras))
    print("  duplicate toc entries removed:", removed)
    print("  footers patched:", patched)


if __name__ == "__main__":
    main(sys.argv[1])
