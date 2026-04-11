#!/bin/bash

ffmpeg \
  -y \
  -i "/mnt/e/code/Cognotik/site/cognotik.com/video_tour/source/Sys_Wizard.mp4" \
  -filter_complex "color=c=black:s=1920x1080:d=3:r=30[intro_bg];
[intro_bg]drawtext=text='System Wizard':fontcolor=white:fontsize=64:x=(w-text_w)/2:y=(h-text_h)/2-60:font=Arial,drawtext=text='AI-Powered Shell Scripting':fontcolor=0xCCCCCC:fontsize=36:x=(w-text_w)/2:y=(h-text_h)/2+20:font=Arial,drawtext=text='Cognotic':fontcolor=0x888888:fontsize=28:x=(w-text_w)/2:y=(h-text_h)/2+80:font=Arial,fade=t=in:st=0:d=0.5[intro_v];
anullsrc=r=44100:cl=stereo:d=3[intro_a];
[0:v]trim=start=23.329:end=27.969,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg0_v];
[0:a]atrim=start=23.329:end=27.969,asetpts=PTS-STARTPTS[seg0_a];
[0:v]trim=start=27.969:end=41.209,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg1_v];
[0:a]atrim=start=27.969:end=41.209,asetpts=PTS-STARTPTS[seg1_a];
[0:v]trim=start=41.95:end=47.179,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg2_v];
[0:a]atrim=start=41.95:end=47.179,asetpts=PTS-STARTPTS[seg2_a];
[0:v]trim=start=49.5:end=52.149,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg3_v];
[0:a]atrim=start=49.5:end=52.149,asetpts=PTS-STARTPTS[seg3_a];
[0:v]trim=start=52.149:end=58.109,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg4_v];
[0:a]atrim=start=52.149:end=58.109,asetpts=PTS-STARTPTS[seg4_a];
[0:v]trim=start=66.308:end=70.638,setpts=(PTS-STARTPTS)/3,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg5_v];
anullsrc=r=44100:cl=stereo:d=1.4433333333333327[seg5_a];
[0:v]trim=start=70.638:end=75.43,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg6_v];
[0:a]atrim=start=70.638:end=75.43,asetpts=PTS-STARTPTS[seg6_a];
[0:v]trim=start=75.43:end=88.989,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg7_v];
[0:a]atrim=start=75.43:end=88.989,asetpts=PTS-STARTPTS[seg7_a];
[0:v]trim=start=88.989:end=98.909,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg8_v];
[0:a]atrim=start=88.989:end=98.909,asetpts=PTS-STARTPTS[seg8_a];
[0:v]trim=start=98.909:end=105.25,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg9_v];
[0:a]atrim=start=98.909:end=105.25,asetpts=PTS-STARTPTS[seg9_a];
[0:v]trim=start=105.25:end=116.638,setpts=(PTS-STARTPTS)/4,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg10_v];
anullsrc=r=44100:cl=stereo:d=2.8470000000000013[seg10_a];
[0:v]trim=start=116.638:end=122.709,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg11_v];
[0:a]atrim=start=116.638:end=122.709,asetpts=PTS-STARTPTS[seg11_a];
[0:v]trim=start=122.709:end=125.588,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg12_v];
[0:a]atrim=start=122.709:end=125.588,asetpts=PTS-STARTPTS[seg12_a];
[0:v]trim=start=125.588:end=128.849,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg13_v];
[0:a]atrim=start=125.588:end=128.849,asetpts=PTS-STARTPTS[seg13_a];
[0:v]trim=start=128.849:end=134.308,setpts=(PTS-STARTPTS)/3,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg14_v];
anullsrc=r=44100:cl=stereo:d=1.8196666666666677[seg14_a];
[0:v]trim=start=134.308:end=139.819,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg15_v];
[0:a]atrim=start=134.308:end=139.819,asetpts=PTS-STARTPTS[seg15_a];
[0:v]trim=start=139.819:end=145,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg16_v];
[0:a]atrim=start=139.819:end=145,asetpts=PTS-STARTPTS[seg16_a];
[0:v]trim=start=145:end=149.679,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg17_v];
[0:a]atrim=start=145:end=149.679,asetpts=PTS-STARTPTS[seg17_a];
[0:v]trim=start=153:end=166.199,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg18_v];
[0:a]atrim=start=153:end=166.199,asetpts=PTS-STARTPTS[seg18_a];
[0:v]trim=start=166.199:end=169.969,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg19_v];
[0:a]atrim=start=166.199:end=169.969,asetpts=PTS-STARTPTS[seg19_a];
[0:v]trim=start=169.969:end=180.479,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg20_v];
[0:a]atrim=start=169.969:end=180.479,asetpts=PTS-STARTPTS[seg20_a];
[0:v]trim=start=184.919:end=187.86,setpts=PTS-STARTPTS,scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:-1:-1:color=black[seg21_v];
[0:a]atrim=start=184.919:end=187.86,asetpts=PTS-STARTPTS[seg21_a];
color=c=black:s=1920x1080:d=4:r=30[outro_bg];
[outro_bg]drawtext=text='Thanks for watching!':fontcolor=white:fontsize=56:x=(w-text_w)/2:y=(h-text_h)/2-40:font=Arial,drawtext=text='Cognotic':fontcolor=0x888888:fontsize=32:x=(w-text_w)/2:y=(h-text_h)/2+40:font=Arial,fade=t=out:st=3:d=1[outro_v];
anullsrc=r=44100:cl=stereo:d=4[outro_a];
[intro_v][intro_a][seg0_v][seg0_a][seg1_v][seg1_a][seg2_v][seg2_a][seg3_v][seg3_a][seg4_v][seg4_a][seg5_v][seg5_a][seg6_v][seg6_a][seg7_v][seg7_a][seg8_v][seg8_a][seg9_v][seg9_a][seg10_v][seg10_a][seg11_v][seg11_a][seg12_v][seg12_a][seg13_v][seg13_a][seg14_v][seg14_a][seg15_v][seg15_a][seg16_v][seg16_a][seg17_v][seg17_a][seg18_v][seg18_a][seg19_v][seg19_a][seg20_v][seg20_a][seg21_v][seg21_a][outro_v][outro_a]concat=n=24:v=1:a=1[outv_raw][outa_raw];
[outa_raw]dynaudnorm=p=0.9:s=5[outa];
[outv_raw]copy[outv]" \
  -map "[outv]" \
  -map "[outa]" \
  -c:v libx264 -preset medium -crf 20 \
  -c:a aac -b:a 192k \
  -movflags +faststart \
  "/mnt/e/code/Cognotik/site/cognotik.com/video_tour/edit/Sys_Wizard.mp4"
