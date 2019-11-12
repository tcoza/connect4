
import java.util.Arrays;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application
{
	static Timeline flashWinners = new Timeline();
	Thread thinking = null;
	int level = 6;
	
	public class Wrapper<T> { T v; public Wrapper(T v) { this.v = v; }}
	
	@Override
	public void start(Stage primaryStage)
	{
		Pane mainPane = new Pane();
		
		mainPane.setBackground(new Background(new BackgroundFill(Color.BLUE, CornerRadii.EMPTY, Insets.EMPTY)));
		
		Circle[][] cells = new Circle[7][6];
		final double CELL_SIZE = 100.0;
		
		Wrapper<Boolean> finished = new Wrapper<>(false);
		
		EventHandler<MouseEvent> cellClick = e ->
		{
			if (!e.getButton().equals(MouseButton.PRIMARY))
				return;
			if (thinking != null && thinking.isAlive())
				return;
			if (finished.v)
				return;
			
			// Player YELLOW's turn
			int yellowMove =  getCoordinates((Circle)e.getSource(), cells)[0];
			System.out.println("Yellow:\t" + yellowMove);
			if (!addDisc(Color.YELLOW, yellowMove, cells))
				return;
			
			checkGameStatus(finished, cells, primaryStage);
			if (finished.v)
				return;
			
			Platform.runLater(() -> primaryStage.setTitle("Thinking ..."));
			
			thinking = new Thread(() ->
			{
				// Player RED's turn
				int redMove = bestMove(cells, level);
				System.out.println("Red:\t" + redMove);
				if (!addDisc(Color.RED, redMove, cells))
					throw new RuntimeException("Your AI is shit");

				checkGameStatus(finished, cells, primaryStage);				
			});
			thinking.start();
		};
		
		// Add cells
		for (int i = 0; i < cells.length; i++)
			for (int j = 0; j < cells[0].length; j++)
			{
				cells[i][j] = new Circle(
						i * CELL_SIZE + CELL_SIZE / 2,
						j * CELL_SIZE + CELL_SIZE / 2,
						(CELL_SIZE / 2) * 0.80,
						Color.LIGHTGRAY);
				cells[i][j].setOnMouseClicked(cellClick);
				
				mainPane.getChildren().add(cells[i][j]);
			}
		
		MenuItem restart = new MenuItem("Restart");
		restart.setOnAction(e ->
		{
			flashWinners.stop();
			thinking = null;
			finished.v = false;
			
			for (int i = 0; i < cells.length; i++)
				for (int j = 0; j < cells[0].length; j++)
					cells[i][j].setFill(Color.LIGHTGRAY);
			primaryStage.setTitle("Ready");
		});
		
		
		Menu levels = new Menu("Select level");
		RadioMenuItem[] levelItems = new RadioMenuItem[10];
		ToggleGroup levelToggleGroup = new ToggleGroup();
		levelToggleGroup.selectedToggleProperty().addListener((e, oldValue, newValue) ->
		{
			for (int i = 0; i < levelItems.length; i++)
				if (newValue == levelItems[i])
					level = i + 1;
		});
		
		for (int i = 1; i <= 10; i++)
		{
			levelItems[i - 1] = new RadioMenuItem("Level " + i);
			levelItems[i - 1].setToggleGroup(levelToggleGroup);
			levels.getItems().add(levelItems[i - 1]);
		}
		levelItems[level - 1].setSelected(true);
		
		ContextMenu mainMenu = new ContextMenu(restart, new SeparatorMenuItem(), levels);
		mainPane.setOnContextMenuRequested(e ->
				mainMenu.show(primaryStage, e.getScreenX(), e.getScreenY()));
		
		primaryStage.setScene(new Scene(mainPane, cells.length * CELL_SIZE, cells[0].length * CELL_SIZE));
		primaryStage.setResizable(true);
		primaryStage.setTitle("Ready");
		primaryStage.show();
	}
	
	static public void checkGameStatus(Wrapper<Boolean> finished, Circle[][] cells, Stage primaryStage)
	{
		String title;
		int[][] winners = getWinners(cells);
		
		if (winners.length != 0)
		{
			Color playerColor = (Color)cells[winners[0][0]][winners[0][1]].getFill();
			
			finished.v = true;
			flashWinners(winners, flashWinners, cells);
			title = (playerColor == Color.RED) ? "Computer wins!" : "You win!";
		}
		else if (firstAvailableColumn(toIntMatrix(cells)) == -1)
		{
			finished.v = true;
			title = "Draw!";
		}
		else
			title = "Ready";
		
		Platform.runLater(() -> primaryStage.setTitle(title));
	}
	
	static final int EMPTY = 0;
	static final int YELLOW = -1;
	static final int RED = +1;
	
	// Return the column of the best move for player RED
	static public int bestMove(Circle[][] cells, int level)
	{
		int[][] matrix = toIntMatrix(cells);
		
		double maxStrength = Double.NEGATIVE_INFINITY;
		int bestMove = firstAvailableColumn(matrix);
		
		if (bestMove == -1)
			return -1;
		
		for (int x = 0; x < matrix.length; x++)
		{
			int[][] tempMatrix = copyMatrix(matrix);
			if (!addDisc(RED, x, tempMatrix))
			{
				//System.out.printf("%12s, ", "N/A");
				continue;
			}
			
			if (getWinners(tempMatrix).length != 0)
				return x;		// WIN!
			
			double strength = getStrength(tempMatrix, -1, level);
			if (strength > maxStrength)
			{
				maxStrength = strength;
				bestMove = x;
			}
			
			//System.out.printf("%12.1f, ", strength);
		}
		return bestMove;
	}
	
	static public int bestMove_minimax(Circle[][] cells, int level)
	{
		int[][] matrix = toIntMatrix(cells);
		
		int maxValue = Integer.MIN_VALUE;
		int bestMove = firstAvailableColumn(matrix);
		
		if (bestMove == -1)
			return -1;
		
		for (int x = 0; x < matrix.length; x++)
		{
			int[][] tempMatrix = copyMatrix(matrix);
			if (!addDisc(RED, x, tempMatrix))
			{
				System.out.print("\t" + "N/A");
				continue;
			}
				
			int value = minimax(tempMatrix, YELLOW, level);
			System.out.print("\t" + value);
			if (value >= maxValue)
			{
				if (value > maxValue)
					bestMove = x;
				else if ((int)(Math.random() * 2) == 1)
					bestMove = x;
				
				maxValue = value;
			}
		}
		
		System.out.println();
		return bestMove;
	}
	
	// Analyzes the strength of the turn = +1 player.
	static public double getStrength(int[][] matrix, int turn, int deepness)
	{
		if (deepness == 0) return 0;
		
		double[] strengths = new double[matrix.length];
		int sIndex = strengths.length;
		
		for (int x = 0; x < matrix.length; x++)
		{
			int[][] tempMatrix = copyMatrix(matrix);
			if (!addDisc(turn == +1 ? RED : YELLOW, x, tempMatrix))
				continue;
			
			int[][] winners = getWinners(tempMatrix);
			if (winners.length != 0)
				strengths[--sIndex] = turn * Math.pow(matrix.length, deepness);
			else
				strengths[--sIndex] = getStrength(tempMatrix, turn * -1, deepness - 1);
		}
		
		if (sIndex == strengths.length)
			return 0;
		
		for (int i = 0; i < strengths.length; i++)
			strengths[i] *= turn;
		
		adjustMoves(strengths, sIndex);
		
		double sum = 0;
		for (int i = sIndex; i < strengths.length; i++)
			sum += strengths[i];
		
		sum *= turn;
		
		return sum;
	}
	
	static public int minimax(int[][] matrix, int turn, int depht)
	{
		if (getWinners(matrix).length != 0)
			return turn * (depht + 1) * -1;
		if (depht == 0)
			return 0;
		
		int bestValue = (turn == RED) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (int x = 0; x < matrix.length; x++)
		{
			int[][] temp_matrix = copyMatrix(matrix);
			
			if (!addDisc(turn, x, temp_matrix))
				continue;
			
			bestValue = (turn == RED) ?
					Math.max(bestValue, minimax(temp_matrix, turn * -1, depht - 1)) :
					Math.min(bestValue, minimax(temp_matrix, turn * -1, depht - 1));
		}
		return bestValue;
	}
	
	// Devalue moves that are unlikely to occur
	static public void adjustMoves(double[] moves, int starti)
	{
		Arrays.sort(moves, starti, moves.length);
		
		// Adjust, as to not underestimate the players
		
		double maxDelta = moves[moves.length - 1] - moves[starti];
		double decay = 1.0;
		
		if (maxDelta == 0)
			return;
		
		for (int i = moves.length - 2; i >= starti; i--)
			moves[i] *= (decay *= (moves[i] - moves[starti]) / maxDelta);
	}
	
	// Returns -1 if full
	static public int firstAvailableColumn(int[][] matrix)
	{
		int column = 0;
		while (column < matrix.length && matrix[column][0] != EMPTY)
			column++;
		
		if (column == matrix.length)
			return -1;
		else
			return column;
	}
	
	// 0 for none, 1 for YELLOW, 2 for RED, -1 otherwise
	static public int[][] toIntMatrix(Circle[][] cells)
	{
		int[][] matrix = new int[cells.length][cells[0].length];
		for (int i = 0; i < matrix.length; i++)
			for (int j = 0; j < matrix[0].length; j++)
				matrix[i][j] =
						cells[i][j].getFill() == Color.LIGHTGRAY ? 0 :
						cells[i][j].getFill() == Color.YELLOW ? YELLOW :
						cells[i][j].getFill() == Color.RED ? RED :
						-1;
		return matrix;
	}
	
	static public int[][] copyMatrix(int[][] matrix)
	{
		int[][] newMatrix = new int[matrix.length][matrix[0].length];
		for (int i = 0; i < newMatrix.length; i++)
			for (int j = 0; j < newMatrix[0].length; j++)
				newMatrix[i][j] = matrix[i][j];
		return newMatrix;
	}
	
	static public void flashWinners(int[][] winners, Timeline flashWinners, Circle[][] cells)
	{
		final Paint winnerColor = cells[winners[0][0]][winners[0][1]].getFill();

		flashWinners.getKeyFrames().clear();
		flashWinners.getKeyFrames().add(new KeyFrame(Duration.millis(200), e ->
		{
			for (int[] winner : winners)
				if (cells[winner[0]][winner[1]].getFill() == Color.LIGHTGRAY)
					cells[winner[0]][winner[1]].setFill(winnerColor);
				else 
					cells[winner[0]][winner[1]].setFill(Color.LIGHTGRAY);
		}));
		flashWinners.setCycleCount(Animation.INDEFINITE);
		flashWinners.play();
	}
	
	static public boolean addDisc(int playerColor, int column, int[][] cells)
	{
		for (int j = cells[column].length - 1; j >= 0; j--)
			if (cells[column][j] == EMPTY)
			{
				cells[column][j] = playerColor;
				return true;
			}
		return false;
	}
	
	static public boolean addDisc(Color playerColor, int column, Circle[][] cells)
	{
		for (int j = cells[column].length - 1; j >= 0; j--)
			if (cells[column][j].getFill() == Color.LIGHTGRAY)
			{
				cells[column][j].setFill(playerColor);
				return true;
			}
		return false;
	}
	
	static public int[] getCoordinates(Circle c, Circle[][] cells)
	{
		int x, y = 0;
		for (x = 0; x < cells.length; x++)
			{
				for (y = 0; y < cells[0].length; y++)
					if (c == cells[x][y])
						break;
				if (y != cells[0].length)
					break;
			}
		
		if (x == cells.length)
			return new int[0];
		else
			return new int[] {x, y};
	}
	
	// Returns array of coordinates (int[point][axis {x:0, y:1}])
	static public int[][] getWinners(int[][] cells)
	{
		final int WIN_LENGTH = 4;		// Connect 4
		int[][] winners = new int[WIN_LENGTH][2];
		
		// Check horizontal wins
		for (int i = 0; i <= cells.length - WIN_LENGTH; i++)
			for (int j = 0; j < cells[0].length; j++)
			{
				if (cells[i][j] == EMPTY)
					continue;
				
				boolean isWin = true;
				for (int k = 1; k < WIN_LENGTH; k++)
					if (cells[i][j] != cells[i + k][j])
					{
						isWin = false;
						break;
					}
				
				if (isWin)
				{
					for (int k = 0; k < WIN_LENGTH; k++)
					{
						winners[k][0] = i + k;
						winners[k][1] = j;
					}
					return winners;
				}
			}
		
		// Check vertical wins
		for (int i = 0; i < cells.length; i++)
			for (int j = 0; j <= cells[0].length - WIN_LENGTH; j++)
			{
				if (cells[i][j] == EMPTY)
					continue;
				
				boolean isWin = true;
				for (int k = 1; k < WIN_LENGTH; k++)
					if (cells[i][j] != cells[i][j + k])
					{
						isWin = false;
						break;
					}
				
				if (isWin)
				{
					for (int k = 0; k < WIN_LENGTH; k++)
					{
						winners[k][0] = i;
						winners[k][1] = j + k;
					}
					return winners;
				}
			}
		
		// Check -45 degree wins
		for (int i = 0; i <= cells.length - WIN_LENGTH; i++)
			for (int j = 0; j <= cells[0].length - WIN_LENGTH; j++)
			{
				if (cells[i][j] == EMPTY)
					continue;
				
				boolean isWin = true;
				for (int k = 1; k < WIN_LENGTH; k++)
					if (cells[i][j] != cells[i + k][j + k])
					{
						isWin = false;
						break;
					}
				
				if (isWin)
				{
					for (int k = 0; k < WIN_LENGTH; k++)
					{
						winners[k][0] = i + k;
						winners[k][1] = j + k;
					}
					return winners;
				}
			}
		
		// Check +45 degree wins
		for (int i = 0; i <= cells.length - WIN_LENGTH; i++)
			for (int j = WIN_LENGTH - 1; j < cells[0].length; j++)
			{
				if (cells[i][j] == EMPTY)
					continue;
				
				boolean isWin = true;
				for (int k = 1; k < WIN_LENGTH; k++)
					if (cells[i][j] != cells[i + k][j - k])
					{
						isWin = false;
						break;
					}
				
				if (isWin)
				{
					for (int k = 0; k < WIN_LENGTH; k++)
					{
						winners[k][0] = i + k;
						winners[k][1] = j - k;
					}
					return winners;
				}
			}
		
		return new int[0][0];
	}
	
	static public int[][] getWinners(Circle[][] cells)
	{
		return getWinners(toIntMatrix(cells));
	}
	
	public static void main(String[] args)
	{
		launch(args);
//		
//		double[] moves = { -12, -11, -3, -2, 8, 8, 10 };
//
//		adjustMoves(moves, 0);
//		
//		System.out.println(Arrays.toString(moves));
//		
//		System.exit(EMPTY);
	}
}
