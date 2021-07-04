package com.jack.voice;

/*
 * wave data中音频数据的  块(段) 结构
 * 
 */
public class BlockHeader {
	/*数据块BLOCK结构
	IMA-ADPCM压缩的音频数据是以数据块存储的，存储格式如下：
	//ADPCM压缩的数据块结构
	typedef __packed struct
	{
	    u16 presample;                		 //第一个采样值16bit
	    u8 index ;                           //上一个数据块的最后一个 index
	    u8 rsv;                              //保留
	    u8 dat[sampleperblock-1];
	}DATA_BLOCK;
	为了数据存储对齐，方便处理，一般一个音频BLOCK的大小是16的整数倍；
	如果设置BLOCK大小为256Byte,减去数据块头长度4字节，还剩252字节，4bit表示一个采样的话，可存储共252x2+1=505个采样点（加上数据头里的一个采样值）。
	对于PCM编码的WAV文件，只需要按照顺序存储原始采样值即可，不需要分块。*/
	/*
	 “data”chuck中的数据是以block形式来组织的，我把它叫做“段”，也就是说在进行压缩时，并不是依次把所有的数据进行压缩保存，而是分段进行的，这样有一个十分重要的好处：那就是在只需要文件中的某一段信息时，可以在解压缩时可以只解所需数据所在的段就行了，没有必要再从文件开始起一个一个地解压缩。这对于处理大文件将有相当的优势。同时，这样也可以保证声音效果。
	Block一般是由block header (block头)和 data两者组成的。其中block header是一个结构，它在单声道下的定义如下：
	Typedef struct{
	   short  sample0;    //block中第一个采样值（未压缩）
	   BYTE  index;     //上一个block最后一个index，第一个block的index=0;
	   BYTE  reserved;   //尚未使用
	}MonoBlockHeader;
	有了blockheader的信息后，就可以不需要知道这个block前面和后面的数据而轻松地解出本block中的压缩数据。对于双声道，它的blockheader应该包含两个MonoBlockHeader其定义如下：
	typedaf struct {
	   MonoBlockHeader leftbher;
	   MonoBlockHeader rightbher;
	}StereoBlockHeader;
	在解压缩时，左右声道是分开处理的，所以必须有两个MonoBlockHeader;
	注1：上述的index是解压缩算法中必须用到的一个参数。详见后面。
	注2: 关于block的大小,通常会有以下几种情况:
	    对于单声道,大小一般为512byte,显然这里面可以保存的sample个数为(512-sizeof(MonoBlockHeader))/4 + 1 = 1017个<其中"+1"是第一个存在头结构中的没有压缩的sample.
	    对于双声道,大小一般为1024byte,按上面的算法可以得出,其中的sample个数也是1017个.
	*/
   private Integer BLOCKPresample;  //block中第一个采样值（未压缩）
   private Integer BLOCKIndex;		 //上一个block最后一个index，第一个block的index=0;
   private Integer BLOCKRSV;	 //尚未使用

   public BlockHeader() {
       this.BLOCKRSV = 0;
   }

   public Integer getBLOCKPresample() {
       return BLOCKPresample;
   }

   public void setBLOCKPresample(Integer BLOCKPresample) {
       this.BLOCKPresample = BLOCKPresample;
   }

   public Integer getBLOCKIndex() {
       return BLOCKIndex;
   }

   public void setBLOCKIndex(Integer BLOCKIndex) {
       this.BLOCKIndex = BLOCKIndex;
   }

   public Integer getBLOCKRSV() {
       return BLOCKRSV;
   }

   public void setBLOCKRSV(Integer BLOCKRSV) {
       this.BLOCKRSV = BLOCKRSV;
   }
}

