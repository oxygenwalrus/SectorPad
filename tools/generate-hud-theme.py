#!/usr/bin/env python3
"""Original native HUD frame geometry. Standard-library only; no source-art input.

Run normally to regenerate checked-in PNGs, or --check to verify byte-for-byte.
The native asset names, canvas sizes and tile padding are compatibility contracts.
No original game or AashPad pixels, masks, palettes or files are loaded.
"""
from __future__ import annotations
import argparse
import binascii
import hashlib
import pathlib
import struct
import zlib

ROOT=pathlib.Path(__file__).resolve().parents[1]
COLORS={
    'PANEL':'101d2b','FIELD':'09121d','KEY':'192e3d','SELECTED':'254858',
    'CYAN':'6bdde7','FOCUS':'ffc878','INK':'e5edf2','MUTED':'a7bbc8','STEEL':'3a5366',
}
def color(name,alpha=255):
    value=COLORS.get(name,name)
    return tuple(int(value[i:i+2],16) for i in (0,2,4))+(alpha,)
CLEAR=(0,0,0,0)

class Canvas:
    def __init__(self,width,height):
        self.width,self.height=width,height
        self.rows=[[CLEAR for _ in range(width)] for _ in range(height)]
    def pixel(self,x,y,value):
        if 0<=x<self.width and 0<=y<self.height:self.rows[y][x]=value
    def rect(self,x,y,width,height,value):
        for py in range(max(0,y),min(self.height,y+height)):
            for px in range(max(0,x),min(self.width,x+width)):self.rows[py][px]=value
    def clipped(self,x,y,width,height,cut,value):
        # Four equal corner cuts; flat interior, no noise or copied material textures.
        for py in range(max(0,y),min(self.height,y+height)):
            for px in range(max(0,x),min(self.width,x+width)):
                u,v=px-x,py-y
                if min(u+v,width-1-u+v,u+height-1-v,width+height-2-u-v)>=cut:
                    self.rows[py][px]=value
    def crop(self,x,y,width,height):
        result=Canvas(width,height)
        result.rows=[row[x:x+width] for row in self.rows[y:y+height]]
        return result
    def polygon(self,points,value):
        # Original integer geometry, evaluated at pixel centers; no external masks.
        for y in range(self.height):
            for x in range(self.width):
                px,py=x+.5,y+.5;inside=False
                previous=points[-1]
                for current in points:
                    ax,ay=previous;bx,by=current
                    if (ay>py)!=(by>py) and px<(bx-ax)*(py-ay)/(by-ay)+ax:inside=not inside
                    previous=current
                if inside:self.pixel(x,y,value)
    def paste(self,other,x,y):
        for py,row in enumerate(other.rows):
            for px,value in enumerate(row):
                if value[3]:self.pixel(x+px,y+py,value)
    def png(self):
        def chunk(kind,data):
            return struct.pack('>I',len(data))+kind+data+struct.pack('>I',binascii.crc32(kind+data)&0xffffffff)
        raw=b''.join(b'\0'+bytes(component for pixel in row for component in pixel) for row in self.rows)
        # Fixed, uncompressed DEFLATE blocks keep output identical across zlib versions.
        blocks=[]
        for offset in range(0,len(raw),65535):
            block=raw[offset:offset+65535]
            blocks.append(bytes([int(offset+len(block)==len(raw))])+struct.pack('<HH',len(block),len(block)^0xffff)+block)
        encoded=b'\x78\x01'+b''.join(blocks)+struct.pack('>I',zlib.adler32(raw)&0xffffffff)
        header=struct.pack('>IIBBBBB',self.width,self.height,8,6,0,0,0)
        return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',header)+chunk(b'IDAT',encoded)+chunk(b'IEND',b'')

PANEL_PARTS={
    'top_left':(0,0),'top':(1,0),'top_right':(2,0),
    'left':(0,1),'center':(1,1),'right':(2,1),
    'bot_left':(0,2),'bot':(1,2),'bot_right':(2,2),
}
BORDER_PARTS={'nw':(0,0),'n':(1,0),'ne':(2,0),'w':(0,1),'e':(2,1),'sw':(0,2),'s':(1,2),'se':(2,2)}

def panel(style):
    tile=Canvas(96,96)
    if style=='00':
        # Preserve the nine-pixel exterior gutter. The native center tile is opaque.
        tile.clipped(9,9,78,78,11,color('STEEL'))
        tile.clipped(11,11,74,74,10,color('KEY'))
        tile.clipped(13,13,70,70,9,color('PANEL'))
        for x in (25,65):tile.rect(x,11,6,1,color('CYAN'))
    else:
        # panel01 is an outline-only family. Filling its transparent center hides content.
        tile.clipped(0,0,96,96,2,color('STEEL'))
        tile.clipped(1,1,94,94,2,color('SELECTED'))
        tile.clipped(3,3,90,90,0,CLEAR)
        for x in (8,81):tile.rect(x,1,7,1,color('CYAN'))
    return {f'ui/bgs/panel{style}_{part}.png':tile.crop(x*32,y*32,32,32) for part,(x,y) in PANEL_PARTS.items()}

def border(style,size):
    tile=Canvas(size*3,size*3)
    cut=3 if size==8 else (1 if style==3 else 0)
    palette=['STEEL','SELECTED','KEY','PANEL','FIELD','FIELD','PANEL','FIELD']
    for depth in range(size):
        alpha=0 if style==3 and depth==3 else 150 if style==4 and depth==3 else 255
        tile.clipped(depth,depth,size*3-depth*2,size*3-depth*2,max(0,cut-depth),color(palette[depth],alpha))
    tile.rect(size,size,size,size,CLEAR)
    # Short corner illumination; edge tiles remain uniform when the game stretches them.
    if size==8:
        for x in (5,size*3-8):tile.rect(x,1,3,1,color('CYAN'))
    return {f'ui/bgs/ui_border{style}_{part}.png':tile.crop(x*size,y*size,size,size) for part,(x,y) in BORDER_PARTS.items()}

def side_extensions():
    result={}
    for side in ('w','e'):
        for end in ('','_top','_bot'):
            tile=Canvas(16,8)
            for x,name in enumerate(('STEEL','SELECTED','KEY','PANEL','FIELD')):
                tile.rect(x if side=='w' else 15-x,0,1,8,color(name))
            if end:
                y=0 if end=='_top' else 7
                tile.rect(0,y,16,1,color('STEEL'))
                tile.rect(5 if side=='w' else 4,y,7,1,color('KEY'))
            result[f'ui/bgs/ui_border1b_{side}{end}.png']=tile
    return result

def tabs():
    result={}
    for side in ('left','right'):
        tile=Canvas(8,34)
        tile.clipped(0,0,16,34,4,color('STEEL'))
        tile.clipped(2,2,14,30,3,color('KEY'))
        tile.clipped(4,4,12,26,2,color('FIELD'))
        if side=='right':tile.rows=[list(reversed(row)) for row in tile.rows]
        result[f'ui/tripad_bot_button_cap_{side}.png']=tile
    fill=Canvas(121,20)
    fill.clipped(0,0,121,20,3,color('KEY'))
    fill.clipped(1,1,119,18,2,color('SELECTED'))
    fill.clipped(1,2,119,17,2,color('KEY'))
    result['ui/tripad_bot_left_button_fill.png']=fill
    # Installed native menu buttons start at x=13 with width=122 and a six-pixel gap.
    # The chassis surrounds those existing hit areas; native text/icons remain on top.
    chassis=Canvas(914,35)
    chassis.clipped(0,0,914,35,4,color('STEEL'))
    chassis.clipped(2,2,910,31,4,color('PANEL'))
    for index in range(7):
        left=11+index*128
        chassis.clipped(left,8,126,25,4,color('STEEL'))
        chassis.clipped(left+1,9,124,23,3,color('FIELD'))
        chassis.rect(left+8,8,9,1,color('CYAN'))
    result['ui/tripad_bot_left_button_widget2.png']=chassis
    location=Canvas(272,30)
    location.clipped(3,0,269,30,3,color('STEEL'))
    location.clipped(4,1,267,28,2,color('PANEL',210))
    location.rect(13,1,20,1,color('CYAN'))
    result['ui/bottomright_location_name_field.png']=location
    return result

def decorative_chassis():
    result={}
    corner=Canvas(32,31)
    corner.polygon([(5,0),(32,0),(32,4),(27,9),(10,9),(4,15),(4,31),(0,31),(0,5)],color('STEEL'))
    corner.polygon([(6,1),(31,1),(31,3),(26,7),(9,7),(2,14),(2,30),(1,30),(1,6)],color('KEY'))
    corner.rect(12,1,10,1,color('CYAN'))
    result['ui/tripad_top_left_decor.png']=corner
    socket=Canvas(64,32)
    socket.clipped(0,0,64,32,3,color('STEEL'))
    socket.clipped(1,1,62,30,3,color('PANEL'))
    socket.clipped(3,5,25,22,2,color('KEY'))
    socket.rect(7,6,12,1,color('CYAN'))
    # The native power button/glow are separate sprites. Keep their socket clear.
    socket.clipped(30,0,33,29,4,CLEAR)
    result['ui/tripad_top_right_power_button_bg.png']=socket
    lower_left=Canvas(280,32)
    lower_left.polygon([(0,0),(6,0),(10,9),(276,9),(280,13),(280,32),(0,32)],color('STEEL'))
    lower_left.polygon([(1,1),(5,1),(9,11),(275,11),(278,14),(278,30),(1,30)],color('PANEL'))
    lower_left.rect(12,12,254,16,color('KEY'))
    lower_left.rect(19,11,22,1,color('CYAN'))
    lower_left.rect(246,27,20,1,color('STEEL'))
    result['ui/tripad_bot_left_decor_extended.png']=lower_left
    lower_right=Canvas(205,19)
    lower_right.polygon([(0,19),(14,4),(197,4),(201,0),(205,0),(205,19)],color('STEEL'))
    lower_right.polygon([(3,18),(15,6),(198,6),(202,2),(204,2),(204,18)],color('PANEL'))
    lower_right.rect(20,7,174,9,color('KEY'))
    lower_right.rect(170,5,19,1,color('CYAN'))
    result['ui/tripad_bot_right_decor2.png']=lower_right
    return result

def assets():
    output={}
    for source in (panel('00'),panel('01'),border(1,8),border(3,4),border(4,4),side_extensions(),tabs(),decorative_chassis()):
        assert not output.keys()&source.keys()
        output.update(source)
    assert len(output)==57
    return output

def verify_geometry(items):
    # Contracts catch accidental fills over transparent native surfaces and tile seam changes.
    for style in ('00','01'):
        center=items[f'ui/bgs/panel{style}_center.png']
        assert all(pixel[3]==(255 if style=='00' else 0) for row in center.rows for pixel in row)
        for name,(x,y) in PANEL_PARTS.items():
            current=items[f'ui/bgs/panel{style}_{name}.png']
            assert (current.width,current.height)==(32,32)
            if y==0 and style=='00':assert all(pixel[3]==0 for row in current.rows[:9] for pixel in row)
            if x==0 and style=='00':assert all(row[i][3]==0 for row in current.rows for i in range(9))
        left=items[f'ui/bgs/panel{style}_top_left.png'];top=items[f'ui/bgs/panel{style}_top.png']
        assert all(left.rows[y][-1]==top.rows[y][0] for y in range(32))
    assert all(items['ui/bgs/ui_border1b_w.png'].rows[y][x][3]==0 for y in range(8) for x in range(5,16))
    assert all(items['ui/bgs/ui_border1b_e.png'].rows[y][x][3]==0 for y in range(8) for x in range(11))
    socket=items['ui/tripad_top_right_power_button_bg.png']
    assert (socket.width,socket.height)==(64,32)
    assert all(socket.rows[y][x][3]==0 for y in range(4,25) for x in range(32,61))
    corner=items['ui/tripad_top_left_decor.png']
    assert all(corner.rows[y][x][3]==0 for y in range(15,31) for x in range(4,32))
    rail=items['ui/tripad_bot_left_decor_extended.png']
    assert all(rail.rows[y][x][3]==0 for y in range(9) for x in range(10,280))
    rail=items['ui/tripad_bot_right_decor2.png']
    assert all(rail.rows[y][x][3]==0 for y in range(4) for x in range(197))

def preview(items):
    result=Canvas(1050,390);result.rect(0,0,1050,390,color('FIELD'))
    for style,ox in [('00',22),('01',540)]:
        for part,(x,y) in PANEL_PARTS.items():
            tile=items[f'ui/bgs/panel{style}_{part}.png']
            xs=range(1,14) if x==1 else [0 if x==0 else 14]
            ys=range(1,7) if y==1 else [0 if y==0 else 7]
            for xx in xs:
                for yy in ys:result.paste(tile,ox+xx*32,18+yy*32)
    result.paste(items['ui/tripad_bot_left_button_widget2.png'],60,303)
    result.paste(items['ui/bottomright_location_name_field.png'],660,354)
    result.paste(items['ui/tripad_top_left_decor.png'],0,0)
    result.paste(items['ui/tripad_top_right_power_button_bg.png'],986,0)
    result.paste(items['ui/tripad_bot_left_decor_extended.png'],60,271)
    result.paste(items['ui/tripad_bot_right_decor2.png'],845,371)
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='verify existing generated files without writing')
    parser.add_argument('--preview',type=pathlib.Path,help='optional assembled original frame preview PNG')
    args=parser.parse_args()
    items=assets();verify_geometry(items)
    for name,canvas in sorted(items.items()):
        path=ROOT/'mod/graphics'/name;data=canvas.png()
        if args.check:
            if not path.is_file() or path.read_bytes()!=data:raise SystemExit(f'Generated texture differs: {path}')
        else:
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    if args.preview:
        args.preview.parent.mkdir(parents=True,exist_ok=True);args.preview.write_bytes(preview(items).png())
    digest=hashlib.sha256(b''.join(name.encode()+items[name].png() for name in sorted(items))).hexdigest()
    print(f'{len(items)} original HUD textures '+('verified' if args.check else 'generated')+f'; geometry checks passed; manifest SHA256 {digest}')

if __name__=='__main__':main()
