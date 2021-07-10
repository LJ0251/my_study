package com.jack.voice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Adpcm {

	/**
	 * 量化器步长 表
	 * 可以用于ADPCM 转 PCM 8bit
	 */
	int[] stepSizeTable = {16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
	                 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173,
	                 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658,
	                 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552};
	/**
	 * 量化器步长 表
	 * 可以用于ADPCM 转 PCM 16 bit
	 */
	private static int[] stepsizeTable = {
	           7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
	           19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
	           50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
	           130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
	           337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
	           876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
	           2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
	           5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
	           15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
	
//	# table of index
	private static int[] indexTable = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
	
	/**
	 * ADPCM 解码   4Bit --> 8bit\16bit
	 * 特点：
	 * 		1、支持8bit\16bit PCM的生成
	 * 		2、使用 stepSizeTable 是  stepsizeTable中的16~1552部分数据
	 * 		3、如果需要16bit PCM,则是忽略掉低4位和高4位数据的值；即低4位和高4位数据均为0；注该方法存在损失。不如使用stepsizeTable的还原度高
	 * 		4、如果需要8bit PCM ,取返回值的 中间8位的数据作为一个采样数据：即 0000 1011 1101 0000，只取 1011 1101
	 * @param code 1byte(包括一个4bit的ADPCM采样数据)
	 * @return short(一个16bit的PCM采样数据)
	 */
	public short ADPCM_Decode(int code, BlockHeader blockheard){
//		# ADPCM_Decode.
//		# code: a byte containing a 4-bit ADPCM sample.
//		# retval : 16-bit ADPCM sample
		int de_index = blockheard.getBLOCKIndex();
		int de_predsample = blockheard.getBLOCKPresample();
	    int step_size = stepSizeTable[de_index];

//	    # inverse code into diff
	    int diffq = step_size >> 3;  //# == step/8
	    if ((code & 4) != 0){
	        diffq += step_size;
	    }

	    if ((code & 2) != 0){
	    	diffq += step_size >> 1;
	    }	        

	    if ((code & 1)!=0){
	    	diffq += step_size >> 2;
	    }	        

//	    # add diff to predicted sample
	    if ((code & 8)!=0){
	    	diffq = -diffq;
	    }	        

	    de_predsample += diffq;

//	    # check for overflow  clip the values to +/- 2^11 (supposed to be 16 bits)
	    if (de_predsample > 2047){
	        de_predsample = 2047;
	    }else if (de_predsample < -2048){
	        de_predsample = -2048;
	    }

//	    # find new quantizer step size
	    de_index += indexTable[code];

//	    # check for overflow
	    if (de_index < 0){
	        de_index = 0;
	    }
	    if (de_index > 48){
	        de_index = 48;
	    }
	    
	    blockheard.setBLOCKIndex(de_index);
		blockheard.setBLOCKPresample(de_predsample);
	    
//	    # save predict sample and de_index for next iteration
//	    # return new decoded sample
//	    # The original algorithm turned out to be 12bit, need to convert to 16bit
	    return (short)(de_predsample << 4);
	}
	
	/**
	 * 
	 * ADPCM(纯压缩数据，非wave规范) 转换为  WAV: vox(ADPCM,没有Header的音频数据) to wav(PCM)
	 * @param voxName 8000HZ 单通道 4bit	ADPCM  案例使用的是voiceData下的 win7_4bit_8k_mono.adpcm
	 * @param wavName 8000HZ 单通道 8bit	PCM    案例生成的是voiceData下的decode_8bit_8k_mono.wav
	 * @param path
	 * @param endian True : big-endian（暂时不可以用）  false : little-endian
	 */
	public void convertAdpcmToWav8Bit(String voxName,String wavName,String path, boolean isBigEndian){
		File in = new File(path+voxName);
		File out = new File(path+wavName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if(!out.exists()){
            	out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            int length =fis.available();
            Header header = new Header(1, 8000, 8); 
            byte[] head = header.writeHeard(length,"PCM8");
            assert head.length == 44;
            fos.write(head);
			int len = fis.available();
			byte[] list_8bit_bytes = new byte[len];
            int rLen = fis.read(list_8bit_bytes, 0, len);
            fis.close();
            BlockHeader blockheard = new BlockHeader();
			// 设置默认的index和rsv
			blockheard.setBLOCKIndex(0);
			blockheard.setBLOCKRSV(0);
			blockheard.setBLOCKPresample(0);
            for(int i=0;i<len;i++){
                byte byte_i = list_8bit_bytes[i];  //# 1 bytes = 8bit
                int high_4bit = (byte_i & 0xf0) >> 4; // # split high 4bit from 8bit
            	int low_4bit = byte_i & 0x0f; // # split low 4bit from 8bit
                //now decode
                short tmpDeS16_0 = ADPCM_Decode(high_4bit, blockheard);
                short tmpDeS16_1 = ADPCM_Decode(low_4bit, blockheard);
                if(isBigEndian){
                	fos.write(Header.HexByteBigEndian(tmpDeS16_0,2));
                	fos.write(Header.HexByteBigEndian(tmpDeS16_1,2));
                }else{
                	tmpDeS16_0 >>= 8;// 将16位(高4位本来就是0)的采样 1、还原为原始计算的12bit(右移4bit)。2、损失低4位(再右移4bit) 
	    			byte b80 = (byte) (tmpDeS16_0 + 0x80);// 将剩余的中间8bit数据的采样加上128(转换为8位有符号数字)，并损失掉高4位。
	    			tmpDeS16_1 >>= 8;
	    			byte b81 = (byte) (tmpDeS16_1 + 0x80);
                	fos.write(b80);
                    fos.write(b81);
                }
            }
            fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}
	
	/**
	 * 
	 * ADPCM(纯压缩数据，非wave规范) 转换为  WAV: vox(ADPCM,没有Header的音频数据) to wav(PCM)
	 * @param voxName 8000HZ 单通道 4bit	ADPCM  案例使用的是voiceData下的 win7_4bit_8k_mono.adpcm
	 * @param wavName 8000HZ 单通道 16bit	PCM    案例生成的是voiceData下的decode_16bit_8k_mono.wav
	 * @param path
	 * @param endian True : big-endian（暂时不可以用）  false : little-endian
	 */
	public void convertAdpcmToWav(String voxName,String wavName,String path, boolean isBigEndian){
		File in = new File(path+voxName);
		File out = new File(path+wavName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if(!out.exists()){
            	out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            int length =fis.available();
            Header header = new Header(1, 8000, 16); 
            byte[] head = header.writeHeard(length,"PCM");
            assert head.length == 44;
            fos.write(head);
			int len = fis.available();
			byte[] list_8bit_bytes = new byte[len];
            int rLen = fis.read(list_8bit_bytes, 0, len);
            fis.close();
            //特殊注释，将这两个作为未压缩数据 进行解压，也能够正常播报
            /**
             * 如果将以下数据打开，则head中的该注释也需要打开
             * 这说明wave文件在识别是否是正确格式时，主要对照的heade的参数以及具体文件的存储格式
             * 目前，猜测，这两个字节数据应该不是未压缩数据。如果强制按照未压缩数据处理，最后文件中应该是噪音数据（因为采用较少，人耳无法感知）
             */
//            fos.write(list_8bit_bytes[1]);
//            fos.write(list_8bit_bytes[0]);
//            for(int i=2;i<len;i++){
            //特殊注释，end
            BlockHeader blockheard = new BlockHeader();
			// 设置默认的index和rsv
			blockheard.setBLOCKIndex(0);
			blockheard.setBLOCKRSV(0);
			blockheard.setBLOCKPresample(0);
            for(int i=0;i<len;i++){
                byte byte_i = list_8bit_bytes[i];  //# 1 bytes = 8bit
                int high_4bit = (byte_i & 0xf0) >> 4; // # split high 4bit from 8bit
            	int low_4bit = byte_i & 0x0f; // # split low 4bit from 8bit
                //now decode
                short tmpDeS16_0 = ADPCM_Decode(high_4bit, blockheard);
                short tmpDeS16_1 = ADPCM_Decode(low_4bit, blockheard);
                if(isBigEndian){
                	fos.write(Header.HexByteBigEndian(tmpDeS16_0,2));
                	fos.write(Header.HexByteBigEndian(tmpDeS16_1,2));
                }else{
                	fos.write(Header.HexByteLittleEndian(tmpDeS16_0,2));
                    fos.write(Header.HexByteLittleEndian(tmpDeS16_1,2));
                }
            }
            fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}

	/**
	 * wave格式的 PCM 转换为 IMA-ADPCM
	 * @param pcmName 	8000HZ 单通道 16bit	PCM    案例使用的是voiceData下的  win7_16bit_8k_mono.wav
	 * @param adpcmName 8000HZ 单通道 4bit		ADPCM  案例生成的是voiceData下的  encode_4bit_8k_mono.wav
	 * @param path
	 * @param isBigEndian
	 */
	public void convertWavePCMToADPCM(String pcmName, String adpcmName, String path, boolean isBigEndian) {
		File in = new File(path + pcmName);
		File out = new File(path + adpcmName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if (!out.exists()) {
				out.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(out);
			long length = in.length();
			long sampleNum = (long) Math.ceil((length-44)/2);
			Header header = new Header(1, 8000, 4);
			byte[] head = header.writeHeard(sampleNum, "ADPCM");
			assert head.length == 60;
			fos.write(head);
			BlockHeader blockheard = new BlockHeader();
			// 设置默认的index和rsv
			blockheard.setBLOCKIndex(0);
			blockheard.setBLOCKRSV(0);
			byte[] headBytes = new byte[2];
			byte[] inData = new byte[1008];
			// 读取前44个字节的PCM编码的WAV头 丢掉
			fis.read(inData, 0, 44);
			
			// 计算包含的 block块的个数（不足一块的向上取整）
			int blockNum = (int) ((length - 44) / 1010);
			if ((length - 44) % 1010 >= 2) {// 至少要包含一个采样数据（2字节）用来做header
				blockNum += 1;
			}
			
			// 设置循环变量
			int len = 0;
			int currentBlockIndex = 1;
			while (currentBlockIndex <= blockNum) {
				// 组织header 数据 START
				len = fis.read(headBytes, 0, 2);// 每个ADPCM 块开头的一个未压缩数据
				blockheard.setBLOCKPresample((headBytes[0] & 0xff) | headBytes[1] << 8);
				// blockheard 的 4 bytes()
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKPresample(), 2));
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKIndex(), 1));
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKRSV(), 1));
				// blockheard 的 4 bytes()
				// 组织header 数据 END

				// blockData 的 252 bytes() START
				if (currentBlockIndex < blockNum) {
					len = fis.read(inData);// 压缩比为4:1，可转换为1008/4=252个字节
				} else if (currentBlockIndex == blockNum && (length - 44) % 1010 > 2) {
					inData = new byte[(int) ((length - 44) % 1010 - 2)];
					len = fis.read(inData);// 读取长度需要减去 2字节的head数据
				} else {
					break;
				}
				if (len != -1) {
					coderRealize(fos, inData, len, blockheard);
				} else {
					break;
				}
				// blockData 的 252 bytes() END

				currentBlockIndex++;

			}
			fis.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * wave格式的 IMA-ADPCM 转换为 PCM
	 * @param adpcmName 8000HZ 单通道 4bit		ADPCM  案例生成的是voiceData下的  wave_4bit_8k_mono.wav
	 * @param pcmName 	8000HZ 单通道 16bit	PCM    案例使用的是voiceData下的  decode_16bit_8k_mono1.wav
	 * @param path
	 * @param isBigEndian
	 */
	public void convertWaveADPCMToPCM(String adpcmName, String pcmName, String path, boolean isBigEndian) {
		File in = new File(path + adpcmName);
		File out = new File(path + pcmName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if (!out.exists()) {
				out.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(out);
			long length = in.length();
			long nBlock = length / 256;
			long sampleNum = nBlock * 505;//每个block块包含505个采样
			long otherBytes = length % 256;
			if (otherBytes != 0) {
				sampleNum += (otherBytes - 4) * 2 + 1;
			}
			//long sampleNum = (long) Math.ceil((length - 44) / 2);
			Header header = new Header(1, 8000, 16);
			//byte[] head = header.writeHeard(sampleNum, "PCM");
			byte[] head = header.writeHeard((long) Math.ceil(sampleNum / 2), "PCM");
			assert head.length == 60;
			fos.write(head);
			// 根据长度信息 组织 目标文件的 头信息
			BlockHeader blockheard = new BlockHeader();
			byte[] inData = new byte[1024];
			// 丢掉 WAVE IMA-ADPCM 的 头信息
			fis.read(inData, 0, 60);
			int len;
			while ((len = fis.read(inData, 0, 256)) != -1) {
				blockheard.setBLOCKPresample(inData[0] | inData[1] << 8);
				blockheard.setBLOCKIndex((int) inData[2]);
				blockheard.setBLOCKRSV((int) inData[3]);
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKPresample(), 2));
				deCoderRealize(fos, inData, len, blockheard);
			}
			fis.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * PCM 编码   16Bit --> 4Bit
	 * 特点：
	 * 		1、使用全量的步差表stepsizeTable
	 * 		2、16bit数据均有值，没有失真
	 * @param fos 输出流
	 * @param inData ADPCM 采样数据
	 * @param len 包含采样个数
	 * @param blockheard 块的头部信息
	 */
	private static void coderRealize(FileOutputStream fos, byte[] inData, int len, BlockHeader blockheard) {
		try {
			int sign;
			int delta;
			int step;
			int valpred;
			int vpdiff;
			int diff;
			int index;
			boolean bufferstep;
			int outputbuffer = 0;
			// 获取上一次的预测值(valpred)和step的索引值(index)
			valpred = blockheard.getBLOCKPresample();
			index = blockheard.getBLOCKIndex();
			//根据上次的index(第一次为0)查表获取step
			step = stepsizeTable[index];
			bufferstep = true;
			for (int i = 0; i < len / 2; i++) {
				int val = (inData[i * 2] & 0xff) | inData[i * 2 + 1] << 8;
				/**
				 *  step 1:求出 当前实际值(val) 与 上一次预测值(valPred)的偏差 diff
				 */
				diff = val - valpred;
				/**
				 *  获取偏差的方向 8表示负向（1000） 0表示正向（0000），用于设置delta的方向
				 *  当 diff 小于0， delta bit3被置1。表示为负向偏差
				 */
				sign = (diff < 0) ? 8 : 0;
				if (sign != 0) {
					diff = (-diff);
				}
				
				/*
				 * step 2:求出delta、vpdiff
				 * 使用公式 1:
				 * 		vpdiff = (delta + 0.5)*step/4
				 * 			    = delta*step/4 + step/8
				 * 				= delta*step/4 + step>>3
				 * 公式2：
				 * 		delta = diff * 4 / step
				 * 		初始化 delta的范围为[0, 7]
				 */
				delta = 0;
				vpdiff = (step >> 3);

				/**
				 * 当diff >= step时 分析如下：
				 *  第一行：delta = 4;解释如下
				 * 	delta = diff * 4 / step  
				 * 		  = (diff-step) * 4 /step + 4 //diff-step>0
				 *  第二行：diff -= step;解释如下
				 *  	重置diff的值，以便后面的进一步计算
				 *  第三行：vpdiff += step;解释如下
				 *  	公式1 与 公式2 联合起来
				 *  	vpdiff = (delta + 0.5)*step/4
				 * 			    = delta*step/4 + step/8
				 * 				= delta*step/4 + step>>3
				 * 				= (diff * 4 / step)*step/4 + step>>3
				 *  			= diff + step>>3
				 *  			= (diff-step) + step + step>>3
				 *      当  (diff-step) 可以忽略为0时
				 *      所以 vpdiff += step;
				 *  经过以上步骤 细分的diff = diff-step;后面继续计算
				 */
				if (diff >= step) {
					delta = 4;
					diff -= step;
					vpdiff += step;
				}
				/**
				 * 将step >>= 1; 即： step = (int)(step / 2);
				 * 此时，当diff >= step时  diff >= (step / 2)
				 * 第一行：delta |= 2;解释如下
				 * 		delta = diff * 4 / step
				 *       	  = ((diff * 4)/2) / (step/2)
				 *    delta*2 = diff * 4 / (step/2)
				 *            = (diff-(step/2))*4 / (step/2) + 4
				 *  delta*2-4 = (diff-(step/2))*4 / (step/2)
				 *  	因此当右边的可忽略为0时：delta*2-4 = 0推出
				 *  				delta = 4/2 = 2
				 *  	即 delta += 2;
				 *  第二行：diff -= step;解释如下
				 *  	重置diff的值，以便后面的进一步计算
				 *  第三行：vpdiff += step;解释如下
				 *  	公式1 与 公式2 联合起来
				 *  	vpdiff = diff + step>>3
				 *             = (diff-(step/2)) + (step/2) + step>>3
				 *      所以 vpdiff += (step/2); 因为step = (int)(step / 2);
				 *      所以 vpdiff += step;
				 */
				step >>= 1;
				if (diff >= step) {
					//delta |= 2;
					delta += 2;
					diff -= step;
					vpdiff += step;
				}
				/**
				 * 继续将step >>= 1; 即：当前 step = step >>= 2; 则 step = (int)(step/4)
				 * 此时，当diff >= step时  diff >= (step / 2)
				 * 第一行：delta |= 2;解释如下
				 * 		delta = diff * 4 / step
				 *       	  = ((diff * 4)/4) / (step/4)
				 *    delta*4 = diff * 4 / (step/4)
				 *            = (diff-(step/4))*4 / (step/4) + 4
				 *  delta*4-4 = (diff-(step/4))*4 / (step/4)
				 *  	因此当右边的可忽略为0时：delta*4-4 = 0推出
				 *  				delta = 4/4 = 1
				 *  	即 delta += 1;
				 *  第二行：diff -= step;解释如下
				 *  	重置diff的值，以便后面的进一步计算
				 *  第三行：vpdiff += step;解释如下
				 *  	公式1 与 公式2 联合起来
				 *  	vpdiff = diff + step>>3
				 *             = (diff-(step/4)) + (step/4) + step>>3
				 *      所以 vpdiff += (step/4); 因为step = (int)(step / 4);
				 *      所以 vpdiff += step;
				 *      
				 *  此时，默认忽略diff-(step/4)的值，因为
				 *  当diff的值是step的n倍(n>=2)时，例如2倍：即diff = 2*step
				 *  	1、在第一次if (diff >= step) 时
				 *  		diff -= step; 即 diff = 2*step -step = 1* step
				 *  		delta = 4
				 *  	2、在第二次if (diff >= step) 时，step >>=1 即 step= step/2
				 *  		diff -= step; 即diff -= (step/2)
				 *  		diff = 0.5*step;
				 *          delta = 4 +2 = 6
				 *      3、在第三次if (diff >= step) 时，又一次step >>=1 即 step= step/4
				 *      	diff -= step; 即diff -= (step/4)
				 *      	diff = 0.25*step;
				 *          delta = 6 +1 = 7
				 *      4、如果继续处理也是无法将diff消除为0的，且delta已经达到最大值 7。
				 *   当diff的值是step的n倍(n<2)时，例如1.5倍：即diff = 1.9*step
				 *      1、在第一次if (diff >= step) 时
				 *  		diff -= step; 即 diff = 1.9*step -step = 0.9* step
				 *  		delta = 4
				 *  	2、在第二次if (diff >= step) 时，step >>=1 即 step= step/2
				 *  		diff -= step; 即diff -= (step/2)
				 *  		diff = 0.4*step;
				 *          delta = 4 +2 = 6
				 *      3、在第三次if (diff >= step) 时，又一次step >>=1 即 step= step/4
				 *      	diff -= step; 即diff -= (step/4)
				 *      	diff = 0.15*step;
				 *          delta = 6 +1 = 7
				 *      4、此时diff已经小于0.15*step可忽略，且delta已经达到最大值 7。
				 */
				step >>= 1;
				if (diff >= step) {
					//delta |= 1;
					delta += 1;
					vpdiff += step;
				}
				/**
				 * step 3：将vpdiff的正负号加上，形成完整的vpdiff
				 * step 4：求出新的预测valpred，即上次预测的valpred+vpdiff
				 */
				if (sign != 0)
					valpred -= vpdiff;
				else
					valpred += vpdiff;
				if (valpred > 32767)
					valpred = 32767;
				else if (valpred < -32768)
					valpred = -32768;
				/**
				 * 量化后的值加上正负号
				 */
				delta |= sign;

				index += indexTable[delta];
				if (index < 0)
					index = 0;
				if (index > 88)
					index = 88;
				step = stepsizeTable[index];
				if (bufferstep) {
					outputbuffer = delta & 0x0f;
				} else {
					fos.write((byte) ((delta << 4) & 0xf0) | outputbuffer);
					// System.out.println("i:"+i);
				}
				bufferstep = !bufferstep;
			}
			if (!bufferstep) {
				fos.write(outputbuffer & 0xff);
				System.out.println("end");
			}
			blockheard.setBLOCKIndex(index);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * ADPCM 解码   4Bit --> 16Bit
	 * 特点：
	 * 		1、使用全量的步差表stepsizeTable
	 * 		2、16bit数据均有值，没有失真
	 * @param fos 输出流
	 * @param inData ADPCM 采样数据
	 * @param len 包含采样个数
	 * @param blockheard 块的头部信息
	 */
	private static void deCoderRealize(FileOutputStream fos, byte[] inData, int len, BlockHeader blockheard) {
		int sign;
		int delta;
		int step;
		int valpred;
		int vpdiff;
		int index;
		boolean bufferstep;
		int j = 4;
		valpred = blockheard.getBLOCKPresample();
		index = blockheard.getBLOCKIndex();
		if (index < 0) {
			index %= 89;
			index = index + 88 + 1;
		} else if (index > 88) {
			index %= 89;
		}
		step = stepsizeTable[index];
		bufferstep = true;
		for (int i = 0; i < (len - 4) * 2; i++) {
			try {
				if (bufferstep) {
					// 取低四位
					delta = inData[j] & 0xf;
				} else {
					// 取高四位
					delta = (inData[j] >> 4) & 0xf;
					j++;
				}
				bufferstep = !bufferstep;
				/**
				 * 根据量化后的值获取 步进的索引值
				 * index 范围[0,88]
				 */
				index += indexTable[delta];
				/**
				 * 值域检查
				 */
				if (index < 0)
					index = 0;
				if (index > 88)
					index = 88;
				
				/**
				 * 取符号位 &（与运算）都为1才为1
				 */
				sign = delta & 8;// sing只会等于8或者0
				// 取数据:除符合位以外的3位数据
				delta = delta & 7;
				// 下边四则运算将vpdiff = (delta+0.5)*step/4四则运算转换成了二进制的与或运算（牛逼）
				/**
				 * coderRealize的逆运算
				 * 当(delta & 4) != 0时  ，即：delta >= 4 (不太清楚的可以自己算下这个与运算)
				 * 	vpdiff = (delta + 0.5)*step/4
				 *         = delta*step/4 + step/8
				 *         = delta*step/4 + step>>3 (当delta >= 4时)
				 *    一种变形 = (delta-4)*step/4 + step + step>>3 (当delta-4 小于1时可忽略(delta-4)*step/4)
				 * 另外一种变形= step*(delta/4) + step>>3 
				 * 		 约   = step + step>>3 
				 *    则vpdiff += step;
				 */
				vpdiff = step >> 3;
				if ((delta & 4) != 0) {
					vpdiff += step;
				}
				/**
				 * 当(delta & 2) != 0时  ，即：delta >= 2 (不太清楚的可以自己算下这个与运算)
				 * 	vpdiff = (delta + 0.5)*step/4
				 *         = delta*step/4 + step/8
				 *         = delta*step/4 + step>>3 (当delta >= 4时)
				 *         = step*(delta/4) + step>>3 
				 *         约= step * 1/2 + step>>3 
				 *    则vpdiff += step >> 1; 即 vpdiff += step/2;
				 */
				if ((delta & 2) != 0) {
					vpdiff += step >> 1;
				}
				/**
				 * 当(delta & 1) != 0时  ，即：delta >= 1 (不太清楚的可以自己算下这个与运算)
				 * 	vpdiff = (delta + 0.5)*step/4
				 *         = delta*step/4 + step/8
				 *         = delta*step/4 + step>>3 (当delta >= 4时)
				 *         = step*(delta/4) + step>>3 
				 *         约= step * 1/4 + step>>3 
				 *    则vpdiff += step >> 2; 即 vpdiff += step/4;
				 */
				if ((delta & 1) != 0) {
					vpdiff += step >> 2;
				}
				
				/**
				 * 给vpdiff加上正负号，并计算预测值
				 *
				 */
				if (sign != 0) {
					valpred -= vpdiff;
				} else {
					valpred += vpdiff;
				}
				/**
				 * 值域检查
				 */
				if (valpred > 32767)
					valpred = 32767;
				else if (valpred < -32768)
					valpred = -32768;
				/**
				 * 获取下一个步差，下次循环使用
				 */
				step = stepsizeTable[index];
				fos.write(Header.HexByteLittleEndian(valpred, 2));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 *  解码的案例
	 * @param args
	 */
	public static void main(String[] args) {
		Adpcm a = new Adpcm();
		// 使用 index范围为[0,48] 的 stepSizeTable
		//解码程序 Test START
//		a.convertAdpcmToWav("win7_4bit_8k_mono.adpcm", "decode_16bit_8k_mono.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//打开特殊注释的转换结果
//		a.convertAdpcmToWav("win7_4bit_8k_mono.adpcm", "decode_16bit_8k_mono_1.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
//		a.convertAdpcmToWav8Bit("win7_4bit_8k_mono.adpcm", "decode_8bit_8k_mono.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		
		// 使用 index范围为[0,88] 的 stepSizeTable
//		a.convertWaveADPCMToPCM("wave_4bit_8k_mono.wav", "decode_16bit_8k_mono1.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//解码程序 Test END
		
		//编码程序 Test START
//		a.convertWavePCMToADPCM("win7_16bit_8k_mono.wav", "encode_4bit_8k_mono1.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//编码程序 Test END
	}
	
}
