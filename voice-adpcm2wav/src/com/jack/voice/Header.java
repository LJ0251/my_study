package com.jack.voice;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * wave Header format
 * @author Administrator
 *
 */
public class Header {
	/*The canonical WAVE format starts with the RIFF header:
	Offset  Size  Name             Description
	0         4   ChunkID          Contains the letters "RIFF" in ASCII form
	                               (0x52494646 big-endian form).
	4         4   ChunkSize        36 + SubChunk2Size, or more precisely:
	                               4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)
	                               This is the size of the rest of the chunk 
	                               following this number.  This is the size of the 
	                               entire file in bytes minus 8 bytes for the
	                               two fields not included in this count:
	                               ChunkID and ChunkSize.
	8         4   Format           Contains the letters "WAVE"
	                               (0x57415645 big-endian form).

	The "WAVE" format consists of two subchunks: "fmt " and "data":
	The "fmt " subchunk describes the sound data's format:

	12        4   Subchunk1ID      Contains the letters "fmt "
	                               (0x666d7420 big-endian form).
	16        4   Subchunk1Size    16 for PCM.  This is the size of the
	                               rest of the Subchunk which follows this number.
	20        2   AudioFormat      PCM = 1 (i.e. Linear quantization)
	                               Values other than 1 indicate some 
	                               form of compression.
	22        2   NumChannels      Mono = 1, Stereo = 2, etc.
	24        4   SampleRate       8000, 44100, etc.
	28        4   ByteRate         == SampleRate * NumChannels * BitsPerSample/8
	32        2   BlockAlign       == NumChannels * BitsPerSample/8
	                               The number of bytes for one sample including
	                               all channels. I wonder what happens when
	                               this number isn't an integer?
	34        2   BitsPerSample    8 bits = 8, 16 bits = 16, etc.
	          2   ExtraParamSize   if PCM, then doesn't exist
	          X   ExtraParams      space for extra parameters

	The "data" subchunk contains the size of the data and the actual sound:

	36        4   Subchunk2ID      Contains the letters "data"
	                               (0x64617461 big-endian form).
	40        4   Subchunk2Size    == NumSamples * NumChannels * BitsPerSample/8
	                               This is the number of bytes in the data.
	                               You can also think of this as the size
	                               of the read of the subchunk following this 
	                               number.
	44        *   Data             The actual sound data.*/
	
	public Header(int numChannels, int sampleRate, int bitsPerSample){
		this.setFormatNumChannels(numChannels);
		this.setFormatSampleRate(sampleRate);// = 8000;
		this.setFormatBitsPerSample(bitsPerSample);// = 16;
	}
	
	public Header(int numChannels, int sampleRate, int bitsPerSample, boolean shape){
		this.setFormatNumChannels(numChannels);
		this.setFormatSampleRate(sampleRate);// = 8000;
		this.setFormatBitsPerSample(bitsPerSample);// = 16;
	}
	
	// the "RIFF"  chunk descriptor START
	/*	   RIFF区块
	名称		偏移地址	字节数	端序	内容
	ID		0x00	4Byte	大端	'RIFF' (0x52494646)  以'RIFF'为标识
	Size	0x04	4Byte	小端	fileSize - 8：整个文件的长度减去ID和Size的长度
	Type	0x08	4Byte	大端	'WAVE'(0x57415645) ：是WAVE表示后面需要两个子块：Format区块和Data区块*/
	private String chunkID = "RIFF";
	private int chunkSize;
	private String format = "WAVE";
	// the "RIFF"  chunk descriptor END
	
	// the "fmt " sub-chunk START
	/*	FORMAT区块
			名称		偏移地址	字节数	端序	内容
			ID		0x00	4Byte	大端	'fmt ' (0x666D7420)：以'fmt '为标识
		Size		0x04	4Byte	小端	16 ：表示该区块数据的长度（不包含ID和Size的长度）
	AudioFormat		0x06	2Byte	小端	音频格式：表示Data区块存储的音频数据的格式，PCM音频数据的值为1，其他值表示某种压缩格式
	NumChannels		0x08	2Byte	小端	声道数：表示音频数据的声道数，1：单声道，2：双声道
	SampleRate		0x0C	4Byte	小端	采样率：表示音频数据的采样率(每秒样本数)  8000HZ 44100HZ
	ByteRate		0x10	4Byte	小端	每秒数据字节数：每秒数据字节数 = SampleRate * NumChannels * BitsPerSample / 8
	BlockAlign		0x14	2Byte	小端	数据块对齐：每个采样所需的字节数 = NumChannels * BitsPerSample / 8
	BitsPerSample	0x16	2Byte	小端	采样位数：每个采样存储的bit数，8：8bit，16：16bit，32：32bit
	cbSize			0x18	2Byte	小端	Size of the extension (0 or 22)，扩展字段长度: 当AudioFormat为非1时存在;    IMA-ADPCM中的的wfmt->cbsize不能忽略，一般取值为2，表示此类型的WAVE FORMAT比一般的WAVE FORMAT多出2个字节。这两个字符也就是nSamplesPerBlock。
	samplesPerBlock	0x1A	2Byte	小端	采样个数*/
	private String formatChunkID = "fmt ";
	private int formatChunkSize = 16;
	private int formatAudioFormat = 1;//PCM =1
	/* Format Code 格式编码
	格式编码		预处理符号					数据
	0x0001		WAVE_FORMAT_PCM			PCM
	0x0003		WAVE_FORMAT_IEEE_FLOAT	Ieee float
	0x0006		WAVE_FORMAT_ALAW		8bits ITU-T G.711 A-law
	0x0007		WAVE_FORMAT_MULAW		8-bit ITU-T G.711 u-law
	0xFFFE		WAVE_FORMAT_EXTENSIBLE
	注意：
	1）WAVE文件默认的字节顺序是小端顺序，若是大端顺序用RIFX代替RIFF标识
	2）取样数据必须是偶数字节
	3）8位取样数据被存储为无符号数，从0到255.16位的取样数据被存储为补码，从-32768到32767
	4）在wave数据流中可能有添加的子块。每一个子块有char SubChunkID[4]，unsigned long SubChunkSize和SubChunkSize字节的数据
	5）RIFF代表Resource Interchange File Format*/
	/*Compression code信息在WAV文件字段的第21、22个byte，通过十六进制查看器我们可以看到一个WAV文件的压缩码类型。对应关系如下表：

	Code　　　　　　	Description 
	0 (0x0000)     　　	Unknown 
	1 (0x0001)     　　	PCM/uncompressed 
	2 (0x0002)     　　	Microsoft ADPCM 
	6 (0x0006)     　　	ITU G.711 a-law 
	7 (0x0007)     　　	ITU G.711 Âµ-law 
	17 (0x0011)   　　	IMA ADPCM 
	20 (0x0016)   　　	ITU G.723 ADPCM (Yamaha) 
	49 (0x0031)   　　	GSM 6.10 
	64 (0x0040)   　　	ITU G.721 ADPCM 
	80 (0x0050)   　　	MPEG 
	65,536 (0xFFFF) 　Experimental*/

	private int formatNumChannels = 1;
	private int formatSampleRate = 8000;
	private int formatBbyteRate = 16000;
	private int formatBlockAlign = 1;
	private int formatBitsPerSample = 16;
	private int formatCbSize; // 子块大小
	private int formatSamplesPerBlock;
	// the "fmt " sub-chunk END
	
	// the "fact" sub-chunk START
	/*在非PCM格式的文件中,一般会在WAVEFORMAT结构后面加入一个“fact”
	 * 即当 AudioFormat 为 非1 时。
	 * 	Header的字节长度为58字节，比原有的44字节多了14个字节。具体如下：
	 * 		1、多一个fact块12字节
	 * 		2、format块多了SamplesPerBlock(2字节)，sbSize(2字节)
	 * */
	/*FACT区块
	名称	偏移地址	字节数	端序		内容
	ID		0x00		4Byte	大端	'fact' (0x66616374):以'fact'为标识
	Size	0x04		4Byte	小端	子块大小
	Data	0x08		4Byte	小端	子块数据大小*/
	private String factChunkID = "fact";// “fact”字符串
	private int factChunkSize = 4;//
	private int factChunkSampleLength = 505; // 
	// the "fact" sub-chunk END
	
	// the "data" sub-chunk START
	/*DATA区块
	名称	偏移地址	字节数	端序	内容
	ID		0x00		4Byte	大端	'data' (0x64617461):以'data'为标识
	Size	0x04		4Byte	小端	N:表示音频数据的长度，N = ByteRate * seconds
	Data	0x08		NByte	小端	音频数据*/
	/*对于Data块，根据声道数和采样率的不同情况，布局如下（每列代表8bits）
	1. 8 Bit 单声道：
	采样1	采样2
	数据1	数据2
	
	2. 8 Bit 双声道
	采样1				采样2	
	声道1数据1	声道2数据1	声道1数据2	声道2数据2
	
	1. 16 Bit 单声道：
	采样1				采样2	
	数据1低字节	数据1高字节	数据1低字节	数据1高字节
	
	2. 16 Bit 双声道
	采样1		 	
	声道1数据1低字节	声道1数据1高字节	声道2数据1低字节	声道2数据1高字节
	采样2	 	 	 
	声道1数据2低字节	声道1数据2高字节	声道2数据2低字节	声道2数据2高字节*/
	private String dataChunkID = "data";
	private int dataChunkSize;
	// the "data" sub-chunk END
	
	/**
	 * 编辑并输出wave文件的头信息
	 * @param fos 输出字节流
	 * @param length 输入文件的数据长度
	 * @param format : ADPCM PCM
	 * @return 
	 */
	public byte[] writeHeard(long length,String format) {
		int dataSize;
		if("ADPCM".equals(format)){// length 是采样个数
			/*
			 * IMA-ADPCM head 格式说明 见附件说明
			 * 一个block 默认为 256字节，存储505个采样（blockHead中的一个未压缩采样 和 blockData 中的 504个采样），一个采样 4Bit
			 */
			int blockHeadNum = (int) (length/505);
			if(length % 505 != 0){ // 存在不足一个block的情况
				blockHeadNum += 1;
			}
			/*
			 * blockHeadNum * 4 : blockHead的数据长度,且存储一个未压缩的采样数据
			 * (length -blockHeadNum)/2 : 除去blockHead中的采样个数 ，/2: 剩余的采样数据的编码后的数据长度(1字节存储2个采样)
			 */
			dataSize = (int) (blockHeadNum * 4 + (length -blockHeadNum)/2);
			if((length -blockHeadNum)%2 != 0){// 存在剩余的采样个数 是 奇数的情况
				dataSize += 1;
			}
			// 初始化待计算的数据
			intAdpcm(dataSize,length);
		}else{
			/*long nBlock = length / 256;
			//为了数据存储对齐，方便处理，一般一个音频BLOCK的大小是16的整数倍；
			//如果设置BLOCK大小为256Byte,减去数据块头长度4字节，还剩252字节，4bit表示一个采样的话，可存储共252x(8/4)+1=505个采样点（加上数据头里的一个采样值）。
			dataSize=(int) (nBlock*505);
			long otherBytes = length % 256;
			if (otherBytes != 0) {
				dataSize +=(otherBytes-4)*2+1;
			}*/
			dataSize = (int) length;
			intPcm(dataSize);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
       try {
    	   
           //写入RIFF块
           bos.write(this.getChunkID().getBytes());
           bos.write(HexByteLittleEndian(this.getChunkSize(),4));
           bos.write(this.getFormat().getBytes());

           //写入‘fmt ’块
           bos.write(this.getFormatChunkID().getBytes());
           bos.write(HexByteLittleEndian(this.getFormatChunkSize(),4));
           bos.write(HexByteLittleEndian(this.getFormatAudioFormat(),2));
           bos.write(HexByteLittleEndian(this.getFormatNumChannels(),2));
           bos.write(HexByteLittleEndian(this.getFormatSampleRate(),4));
           bos.write(HexByteLittleEndian(this.getFormatBbyteRate(),4));
           bos.write(HexByteLittleEndian(this.getFormatBlockAlign(),2));
           bos.write(HexByteLittleEndian(this.getFormatBitsPerSample(),2));
           if("ADPCM".equals(format)){
               bos.write(HexByteLittleEndian(this.getFormatCbSize(),2));
               bos.write(HexByteLittleEndian(this.getFormatSamplesPerBlock(),2));
        	   
        	   //写入FACT块
               bos.write(this.getFactChunkID().getBytes());
               bos.write(HexByteLittleEndian(this.getFactChunkSize(),4));
               bos.write(HexByteLittleEndian(this.getFactChunkSampleLength(),4));
           }

           //写入Data块
           bos.write(this.getDataChunkID().getBytes());
           bos.write(HexByteLittleEndian(this.getDataChunkSize(),4));
           
       } catch (IOException e) {
           e.printStackTrace();
       }
       return bos.toByteArray();
	}

	private void intPcm(int dataSize) {
		this.setFormatChunkSize(16);
		this.setFormatAudioFormat(1);//PCM =1
		this.setFormatBbyteRate(this.getFormatSampleRate() * this.getFormatNumChannels() * this.getFormatBitsPerSample() / 8);// = 16000;
		this.setFormatBlockAlign(this.getFormatNumChannels() * this.getFormatBitsPerSample() / 8);// = 1;
		this.setDataChunkSize((dataSize-2) * 4 +2);// 第一个2 是前两个未压缩数据，*4 是1个字节解压为4个字节（2个16位数字），+2是加上未压缩数据
		this.setChunkSize(this.getDataChunkSize() + 36);
	}

	private void intAdpcm(int dataSize, long length) {
		this.setFormatChunkSize(20);
		this.setFormatAudioFormat(17);//IMA-ADPCM =17

		this.setFormatBbyteRate(4055);// = 4055 不知道具体原因 ？？
		this.setFormatBlockAlign(256);// = 256不知道具体原因？？
		this.setFormatCbSize(2); // 扩展块的大小(format块中 此块后的属于扩展块)
		this.setFormatSamplesPerBlock(505);
		
		this.setFactChunkSampleLength((int) length);// 实际采样点的个数
		this.setDataChunkSize(dataSize);// 编码后的数据字节数
		this.setChunkSize(dataSize + 52);// 编码后的总文件长度 - 8(riff块的前两个数据)
	}

	/**
	*  将int型（16进制）大端数据 转换为 len个 小端数据
	*  
	* @param in
	* @param len
	* @return byte[len]
	*/
	public static byte[] HexByteLittleEndian(int in,int len) {
		assert len<=4;
		byte[] b = new byte[len];
		for (int i = 0; i < len; i++) {
			b[i] = (byte) (in >> (i * 8) & 0x00ff);
		}
		/*System.out.print("LittleEndian: ");
		for (byte c : b) {
			System.out.print("0x" + Integer.toHexString(c & 0xFF) + ",");
		}
		System.out.println();*/
		return b;
	}
	
   /** 
	* 将int型（16进制）大端数据 转换为 len 个 byte
	* @param in
	* @param len
	* @return byte[len]
	*/
	public static byte[] HexByteBigEndian(int in,int len) {
		assert len<=4;
		byte[] b = new byte[len];
		for (int i = 0; i < len; i++) {
			b[i] = (byte) (in >> ((len-i-1) * 8) & 0x00ff);
		}
		/*System.out.print("BigEndian: ");
		for (byte c : b) {
			System.out.print("0x" + Integer.toHexString(c & 0xFF) + ",");

		}
		System.out.println();*/
		return b;
	}
	
	/**
    *  将int型（16进制）大端数据  全部转换为 小端数据
    *  
    * @param in
    * @return byte[4]
    */
   public static byte[] HexByteBigToSmall4(int in) {
       int len = 4;
       byte[] b = new byte[len];
       for (int i = 0; i < len; i++) {
           b[i] = (byte) (in >> (i * 8) & 0x00ff);
       }
       return b;
   }
   /**
    * 将int型（16进制）大端数据  低16位转换为 小端数据
    * @param in
    * @return byte[2]
    */
   public static byte[] HexByteBigToSmall2(int in) {
       int len = 2;
       byte[] b = new byte[len];
       for (int i = 0; i < len; i++) {
           b[i] = (byte) (in >> (i * 8) & 0x00ff);
       }
       return b;
   }

   /**
    * 将int型（16进制）大端数据  低8位转换为 小端数据
    * @param in
    * @return byte[1]
    */
   public static byte HexByteBigToSmall(int in) {
       byte b = (byte) (in & 0x00ff);
       return b;
   }
	 
	public void setFormatCbSize(int formatCbSize) {
		this.formatCbSize = formatCbSize;
	}

	public String getChunkID() {
		return chunkID;
	}
	public void setChunkID(String chunkID) {
		this.chunkID = chunkID;
	}
	public int getChunkSize() {
		return chunkSize;
	}
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}
	public String getFormat() {
		return format;
	}
	public void setFormat(String format) {
		this.format = format;
	}
	public String getFormatChunkID() {
		return formatChunkID;
	}
	public void setFormatChunkID(String formatChunkID) {
		this.formatChunkID = formatChunkID;
	}
	public int getFormatChunkSize() {
		return formatChunkSize;
	}
	public void setFormatChunkSize(int formatChunkSize) {
		this.formatChunkSize = formatChunkSize;
	}
	public int getFormatAudioFormat() {
		return formatAudioFormat;
	}
	public void setFormatAudioFormat(int formatAudioFormat) {
		this.formatAudioFormat = formatAudioFormat;
	}
	public int getFormatNumChannels() {
		return formatNumChannels;
	}
	public void setFormatNumChannels(int formatNumChannels) {
		this.formatNumChannels = formatNumChannels;
	}
	public int getFormatSampleRate() {
		return formatSampleRate;
	}
	public void setFormatSampleRate(int formatSampleRate) {
		this.formatSampleRate = formatSampleRate;
	}
	public int getFormatBbyteRate() {
		return formatBbyteRate;
	}
	public void setFormatBbyteRate(int formatBbyteRate) {
		this.formatBbyteRate = formatBbyteRate;
	}
	public int getFormatBlockAlign() {
		return formatBlockAlign;
	}
	public void setFormatBlockAlign(int formatBlockAlign) {
		this.formatBlockAlign = formatBlockAlign;
	}
	public int getFormatBitsPerSample() {
		return formatBitsPerSample;
	}
	public void setFormatBitsPerSample(int formatBitsPerSample) {
		this.formatBitsPerSample = formatBitsPerSample;
	}
	public int getFormatCbSize() {
		return formatCbSize;
	}
	public void setFormatbSize(int formatCbSize) {
		this.formatCbSize = formatCbSize;
	}
	public int getFormatSamplesPerBlock() {
		return formatSamplesPerBlock;
	}
	public void setFormatSamplesPerBlock(int formatSamplesPerBlock) {
		this.formatSamplesPerBlock = formatSamplesPerBlock;
	}
	public String getFactChunkID() {
		return factChunkID;
	}
	public void setFactChunkID(String factChunkID) {
		this.factChunkID = factChunkID;
	}
	public int getFactChunkSize() {
		return factChunkSize;
	}
	public void setFactChunkSize(int factChunkSize) {
		this.factChunkSize = factChunkSize;
	}
	public int getFactChunkSampleLength() {
		return factChunkSampleLength;
	}
	public void setFactChunkSampleLength(int factChunkSampleLength) {
		this.factChunkSampleLength = factChunkSampleLength;
	}
	public String getDataChunkID() {
		return dataChunkID;
	}
	public void setDataChunkID(String dataChunkID) {
		this.dataChunkID = dataChunkID;
	}
	public int getDataChunkSize() {
		return dataChunkSize;
	}
	public void setDataChunkSize(int dataChunkSize) {
		this.dataChunkSize = dataChunkSize;
	}
	
}
